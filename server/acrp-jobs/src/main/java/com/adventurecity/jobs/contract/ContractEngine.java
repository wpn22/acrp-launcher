package com.adventurecity.jobs.contract;

import com.adventurecity.jobs.ACRPJobsPlugin;
import com.adventurecity.jobs.config.ContractDefinition;
import com.adventurecity.jobs.config.JobDefinition;
import com.adventurecity.jobs.config.JobGrade;
import com.adventurecity.jobs.config.StepDefinition;
import com.adventurecity.jobs.config.StepType;
import com.adventurecity.jobs.config.Zone;
import com.adventurecity.jobs.economy.Payout;
import com.adventurecity.jobs.spot.WorkSpot;
import com.adventurecity.jobs.storage.PlayerData;
import com.adventurecity.jobs.storage.PlayerDataManager;
import com.adventurecity.jobs.storage.TxType;
import com.adventurecity.jobs.training.TrainingTrigger;
import com.adventurecity.jobs.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Runs the active contracts: resolves their targets, watches for step completion every half second,
 * drives the HUD, and pays out at the end.
 *
 * <p>All of it is generic - the steps come from YAML, so police, EMS and mechanic jobs in later
 * phases plug into this same loop without touching it.</p>
 */
public final class ContractEngine {

    private static final String JOB_ITEM_TAG = ChatColor.DARK_GRAY + "غرض وظيفة";

    private final ACRPJobsPlugin plugin;
    private final Map<UUID, ActiveContract> active = new HashMap<UUID, ActiveContract>();
    private final Random random = new Random();

    public ContractEngine(ACRPJobsPlugin plugin) {
        this.plugin = plugin;
    }

    public ActiveContract get(Player player) {
        return active.get(player.getUniqueId());
    }

    public boolean hasContract(Player player) {
        return active.containsKey(player.getUniqueId());
    }

    // ---------------------------------------------------------------- start / cancel

    /**
     * @param dispatchCaller the player who requested this job (taxi passenger), or null
     * @return true when the contract actually started
     */
    public boolean start(Player player, JobDefinition job, ContractDefinition definition, UUID dispatchCaller) {
        PlayerData data = plugin.players().get(player);
        if (data == null) {
            return false;
        }
        if (!data.onDuty()) {
            plugin.msg().send(player, "duty.not-on-duty");
            return false;
        }
        if (active.containsKey(player.getUniqueId())) {
            plugin.msg().send(player, "contract.active");
            return false;
        }

        long cooldown = plugin.cooldowns().remaining(player.getUniqueId(), definition.id());
        if (cooldown > 0L) {
            plugin.msg().send(player, "contract.cooldown", "seconds", cooldown);
            return false;
        }

        long cap = plugin.settings().dailyEarningCap;
        if (cap > 0L && data.earnedToday(PlayerDataManager.epochDay()) >= cap) {
            plugin.msg().send(player, "contract.daily-cap");
            return false;
        }

        Zone[] zones = resolveZones(definition);
        if (zones == null) {
            plugin.msg().send(player, "contract.no-zone");
            plugin.getLogger().warning("[ACRPJobs] Contract " + job.id() + "/" + definition.id()
                    + " cannot start: a zone or zone group it needs is not placed yet.");
            return false;
        }

        ActiveContract contract = new ActiveContract(player.getUniqueId(), job, definition, zones);
        contract.dispatchPlayer(dispatchCaller);
        contract.lastCheckpoint(player.getLocation());
        active.put(player.getUniqueId(), contract);

        plugin.training().onAction(player, TrainingTrigger.CONTRACT_START);
        plugin.msg().send(player, "contract.started", "contract", Msg.color(definition.name()));
        plugin.msg().send(player, "contract.next-step", "step", contract.step().title());
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_PLING, 1.0F, 1.4F);
        return true;
    }

    /** Resolves one zone per step; returns null when a step that needs a zone cannot get one. */
    private Zone[] resolveZones(ContractDefinition definition) {
        List<StepDefinition> steps = definition.steps();
        Zone[] zones = new Zone[steps.size()];
        Zone previous = null;
        for (int i = 0; i < steps.size(); i++) {
            StepDefinition step = steps.get(i);
            Zone zone;
            if (step.zone() != null && !step.zone().isEmpty()) {
                zone = plugin.zones().get(step.zone());
            } else if (step.zoneGroup() != null && !step.zoneGroup().isEmpty()) {
                zone = plugin.zones().randomFromGroup(step.zoneGroup(), previous);
            } else {
                zone = previous;
            }
            if (zone == null && step.needsZone()) {
                return null;
            }
            zones[i] = zone;
            if (zone != null) {
                previous = zone;
            }
        }
        return zones;
    }

    public void cancel(Player player, boolean announce) {
        ActiveContract contract = active.remove(player.getUniqueId());
        if (contract == null) {
            if (announce) {
                plugin.msg().send(player, "contract.none-active");
            }
            return;
        }
        clearJobItems(player, contract);
        plugin.hud().clear(player);
        if (contract.dispatchPlayer() != null) {
            plugin.dispatch().releaseDriver(contract.dispatchPlayer());
        }
        if (announce) {
            plugin.msg().send(player, "contract.cancelled");
        }
    }

    /** Called when a player logs off mid-contract. */
    public void abandon(UUID playerId) {
        ActiveContract contract = active.remove(playerId);
        if (contract != null && contract.dispatchPlayer() != null) {
            plugin.dispatch().releaseDriver(contract.dispatchPlayer());
        }
    }

    /** /jobs confirm - the manual half of CONFIRM steps. */
    public void confirm(Player player) {
        ActiveContract contract = active.get(player.getUniqueId());
        if (contract == null) {
            plugin.msg().send(player, "contract.none-active");
            return;
        }
        StepDefinition step = contract.step();
        if (step.type() != StepType.CONFIRM) {
            plugin.msg().send(player, "contract.confirm-nothing");
            return;
        }
        // Without this, confirming in the split second after the passenger disconnects would pay a
        // fare for a ride nobody took.
        if (step.targetPlayer() && contract.dispatchPlayer() != null && passenger(contract) == null) {
            passengerGone(player, contract);
            return;
        }
        Location target = targetLocation(contract);
        double radius = radiusOf(contract, step);
        if (target != null && !within(player.getLocation(), target, radius)) {
            plugin.msg().send(player, "contract.confirm-far");
            return;
        }
        completeStep(player, contract);
    }

    // ---------------------------------------------------------------- tick

    /** Called twice a second by a repeating task. */
    public void tick() {
        if (active.isEmpty()) {
            return;
        }
        List<UUID> stale = new ArrayList<UUID>();
        for (Map.Entry<UUID, ActiveContract> entry : new ArrayList<Map.Entry<UUID, ActiveContract>>(active.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                stale.add(entry.getKey());
                continue;
            }
            evaluate(player, entry.getValue());
        }
        for (UUID id : stale) {
            abandon(id);
        }
    }

    private void evaluate(Player player, ActiveContract contract) {
        StepDefinition step = contract.step();
        Location playerLocation = player.getLocation();
        double radius = radiusOf(contract, step);
        Location target = targetLocation(contract);
        String hint = null;

        switch (step.type()) {
            case GOTO_ZONE:
                if (target != null && within(playerLocation, target, radius)) {
                    completeStep(player, contract);
                    return;
                }
                break;

            case GOTO_PLAYER: {
                Player passenger = passenger(contract);
                if (passenger == null) {
                    passengerGone(player, contract);
                    return;
                }
                if (within(playerLocation, passenger.getLocation(), radius)) {
                    completeStep(player, contract);
                    return;
                }
                break;
            }

            case PICKUP_ITEM:
                if (target == null || within(playerLocation, target, radius)) {
                    if (giveJobItem(player, contract, step)) {
                        completeStep(player, contract);
                        return;
                    }
                    hint = plugin.msg().get("contract.inventory-full");
                }
                break;

            case DELIVER_ITEM:
                if (target != null && within(playerLocation, target, radius)) {
                    if (removeJobItems(player, contract, step.amount())) {
                        completeStep(player, contract);
                        return;
                    }
                    hint = plugin.msg().get("contract.need-item", "item",
                            contract.carriedItem() == null ? "-" : itemLabel(contract.carriedItem()));
                }
                break;

            case WAIT_TIMER: {
                boolean inPlace = target == null || within(playerLocation, target, radius);
                if (!inPlace) {
                    contract.waitStartedAt(0L);
                    break;
                }
                if (contract.waitStartedAt() == 0L) {
                    contract.waitStartedAt(System.currentTimeMillis());
                }
                long elapsed = System.currentTimeMillis() - contract.waitStartedAt();
                if (elapsed >= step.seconds() * 1000L) {
                    completeStep(player, contract);
                    return;
                }
                break;
            }

            case CLEAR_SPOTS: {
                // Completion is pushed in by SpotService when the worker actually clears something;
                // all this does is point them at the nearest one and show the tally.
                WorkSpot nearest = plugin.spots().nearestActive(player, step.pool());
                if (nearest != null) {
                    target = plugin.spots().locationOf(nearest);
                } else {
                    hint = plugin.msg().get("contract.spot-none");
                }
                if (hint == null) {
                    hint = plugin.msg().get("contract.spot-progress",
                            "done", contract.spotsCleared(), "total", step.amount());
                }
                break;
            }

            case CONFIRM: {
                if (step.targetPlayer() && passenger(contract) == null && contract.dispatchPlayer() != null) {
                    passengerGone(player, contract);
                    return;
                }
                hint = ChatColor.YELLOW + "اكتب /jobs confirm";
                break;
            }

            default:
                break;
        }

        updateHud(player, contract, target, hint);
    }

    private void updateHud(Player player, ActiveContract contract, Location target, String hint) {
        StepDefinition step = contract.step();
        String info;
        double progress;

        if (step.type() == StepType.CLEAR_SPOTS) {
            progress = step.amount() <= 0 ? 0.0D
                    : (double) contract.spotsCleared() / (double) step.amount();
            info = contract.spotsCleared() + "/" + step.amount();
        } else if (step.type() == StepType.WAIT_TIMER && contract.waitStartedAt() > 0L) {
            long elapsed = System.currentTimeMillis() - contract.waitStartedAt();
            long total = step.seconds() * 1000L;
            progress = (double) elapsed / (double) total;
            info = Math.max(0L, (total - elapsed) / 1000L) + "ث";
        } else {
            progress = (double) contract.index() / (double) contract.stepCount();
            if (target != null && target.getWorld() != null
                    && target.getWorld().equals(player.getWorld())) {
                info = plugin.msg().get("hud.distance", "distance",
                        (int) target.distance(player.getLocation()));
            } else {
                info = (contract.index() + 1) + "/" + contract.stepCount();
            }
        }

        plugin.hud().update(player, plugin.msg().get("hud.objective",
                "step", step.title(), "info", info), progress);
        plugin.hud().actionBar(player, hint != null ? hint : ChatColor.GRAY + step.title());
        if (target != null) {
            plugin.hud().beam(player, target);
        }
    }

    /**
     * Called by the work-spot system whenever a player clears a spot. Only counts toward the step
     * that actually asked for that pool, so cleaning a stain does not advance a rubbish round.
     */
    public void onSpotCleared(Player player, String poolId) {
        ActiveContract contract = active.get(player.getUniqueId());
        if (contract == null || contract.finished()) {
            return;
        }
        StepDefinition step = contract.step();
        if (step.type() != StepType.CLEAR_SPOTS || !step.pool().equalsIgnoreCase(poolId)) {
            return;
        }
        int done = contract.addSpotCleared();
        plugin.training().onAction(player, TrainingTrigger.SPOT_CLEARED);
        if (done >= step.amount()) {
            completeStep(player, contract);
            return;
        }
        plugin.msg().send(player, "contract.spot-cleared", "done", done, "total", step.amount());
    }

    // ---------------------------------------------------------------- step completion

    private void completeStep(Player player, ActiveContract contract) {
        StepDefinition step = contract.step();
        Location here = player.getLocation();

        if (step.markRideStart()) {
            contract.rideStart(here);
            plugin.msg().send(player, "taxi.ride-started");
        }
        checkTravelSanity(player, contract, here);

        plugin.msg().send(player, "contract.step-done", "step", step.title());
        player.playSound(here, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8F, 1.5F);
        plugin.hud().celebrate(player, here);

        contract.advance(here);
        if (contract.finished()) {
            complete(player, contract);
            plugin.training().onAction(player, TrainingTrigger.CONTRACT_DONE);
        } else {
            plugin.msg().send(player, "contract.next-step", "step", contract.step().title());
            plugin.training().onAction(player, TrainingTrigger.STEP_DONE);
        }
    }

    /**
     * Flags impossible travel between checkpoints. Only long jumps are considered so ordinary lag or
     * a short elytra hop never costs an honest player their pay.
     */
    private void checkTravelSanity(Player player, ActiveContract contract, Location here) {
        Location previous = contract.lastCheckpoint();
        if (previous == null) {
            return;
        }
        if (previous.getWorld() == null || !previous.getWorld().equals(here.getWorld())) {
            flagSuspicious(player, contract, "تغيير عالم أثناء المهمة");
            return;
        }
        double distance = previous.distance(here);
        int seconds = Math.max(1, contract.stepElapsedSeconds());
        if (distance > 100.0D && distance / seconds > plugin.settings().maxBlocksPerSecond) {
            flagSuspicious(player, contract, String.format("%.0f بلوك في %d ثانية", distance, seconds));
        }
    }

    private void flagSuspicious(Player player, ActiveContract contract, String detail) {
        contract.flagSuspicious();
        if (plugin.settings().logSuspicious) {
            plugin.getLogger().warning("[ACRPJobs] Suspicious contract movement: " + player.getName()
                    + " (" + contract.job().id() + "/" + contract.definition().id() + ") - " + detail);
        }
    }

    private void complete(Player player, ActiveContract contract) {
        active.remove(player.getUniqueId());
        plugin.hud().clear(player);

        PlayerData data = plugin.players().get(player);
        ContractDefinition definition = contract.definition();
        JobDefinition job = contract.job();
        int duration = contract.elapsedSeconds();

        if (contract.dispatchPlayer() != null) {
            plugin.dispatch().releaseDriver(contract.dispatchPlayer());
        }
        clearJobItems(player, contract);
        plugin.cooldowns().set(player.getUniqueId(), definition.id(), definition.cooldownSeconds());

        if (data == null) {
            return;
        }

        boolean tooFast = duration < plugin.settings().minContractSeconds;
        if (tooFast || contract.suspicious()) {
            plugin.msg().send(player, "contract.too-fast");
            if (plugin.settings().logSuspicious) {
                plugin.getLogger().warning("[ACRPJobs] No payout for " + player.getName() + " on "
                        + job.id() + "/" + definition.id() + " (duration " + duration + "s, suspicious="
                        + contract.suspicious() + ").");
            }
            return;
        }

        long gross = definition.basePay();
        if (definition.randomBonus() > 0L) {
            gross += random.nextInt((int) Math.min(Integer.MAX_VALUE, definition.randomBonus() + 1L));
        }
        gross += rideDistancePay(contract, player.getLocation());

        JobGrade grade = plugin.jobManager().gradeOf(data, job);
        gross = Math.round(gross * grade.payMultiplier());

        String type = definition.chargePassenger() ? TxType.FARE : TxType.CONTRACT;
        long payable = gross;
        if (definition.chargePassenger() && contract.dispatchPlayer() != null) {
            payable = chargePassenger(player, contract, gross);
        }

        Payout payout = plugin.payouts().pay(data, payable, job.payrollTaxPercent(), type,
                job.id() + "/" + definition.id());

        if (payout.paid()) {
            plugin.msg().send(player, "contract.completed", "pay", plugin.economy().format(payout.net));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7F, 1.4F);
        }
        if (payout.capReached) {
            plugin.msg().send(player, "contract.daily-cap");
        }
        plugin.jobManager().addXp(player, job, definition.xpReward());

        final UUID playerId = player.getUniqueId();
        final String jobId = job.id();
        final String contractId = definition.id();
        final long paid = payout.net;
        final int seconds = duration;
        plugin.players().submit(() ->
                plugin.players().storage().logContract(playerId, jobId, contractId, paid, seconds));
    }

    /** Distance component of a fare, in AC. */
    private long rideDistancePay(ActiveContract contract, Location end) {
        double perBlock = contract.definition().payPerBlock();
        Location start = contract.rideStart();
        if (perBlock <= 0.0D || start == null || start.getWorld() == null
                || !start.getWorld().equals(end.getWorld())) {
            return 0L;
        }
        return Math.round(perBlock * start.distance(end));
    }

    /**
     * Takes the fare out of the passenger's wallet. Anything they genuinely cannot cover is topped
     * up by the city, but never by more than the contract's base pay.
     */
    private long chargePassenger(Player driver, ActiveContract contract, long fare) {
        UUID passengerId = contract.dispatchPlayer();
        long balance = plugin.economy().balance(passengerId);
        long charged = Math.min(fare, balance);
        if (charged > 0L) {
            plugin.economy().withdraw(passengerId, charged, TxType.FARE_PAID,
                    "أجرة تاكسي - " + driver.getName());
            Player passenger = Bukkit.getPlayer(passengerId);
            if (passenger != null) {
                plugin.msg().send(passenger, "taxi.fare-paid", "amount", plugin.economy().format(charged));
                plugin.msg().send(driver, "taxi.fare-received",
                        "amount", plugin.economy().format(charged),
                        "player", passenger.getName());
            }
        }
        long shortfall = fare - charged;
        if (shortfall > 0L) {
            plugin.msg().send(driver, "taxi.fare-subsidy");
            return charged + Math.min(shortfall, contract.definition().basePay());
        }
        return charged;
    }

    private void passengerGone(Player driver, ActiveContract contract) {
        active.remove(driver.getUniqueId());
        plugin.hud().clear(driver);
        if (contract.dispatchPlayer() != null) {
            plugin.dispatch().releaseDriver(contract.dispatchPlayer());
        }
        clearJobItems(driver, contract);
        plugin.msg().send(driver, "taxi.passenger-left");
    }

    private Player passenger(ActiveContract contract) {
        UUID id = contract.dispatchPlayer();
        if (id == null) {
            return null;
        }
        Player player = Bukkit.getPlayer(id);
        return player != null && player.isOnline() ? player : null;
    }

    // ---------------------------------------------------------------- targets

    /** Where the current step points: a zone centre, or the dispatch player when the step follows them. */
    private Location targetLocation(ActiveContract contract) {
        StepDefinition step = contract.step();
        if (step.type() == StepType.GOTO_PLAYER || (step.type() == StepType.CONFIRM && step.targetPlayer())) {
            Player passenger = passenger(contract);
            return passenger == null ? null : passenger.getLocation();
        }
        Zone zone = contract.zone();
        return zone == null ? null : zone.toLocation();
    }

    private double radiusOf(ActiveContract contract, StepDefinition step) {
        if (step.radius() > 0.0D) {
            return step.radius();
        }
        Zone zone = contract.zone();
        return zone == null ? 6.0D : zone.radius();
    }

    private static boolean within(Location from, Location to, double radius) {
        if (from == null || to == null || from.getWorld() == null || to.getWorld() == null) {
            return false;
        }
        if (!from.getWorld().equals(to.getWorld())) {
            return false;
        }
        return from.distanceSquared(to) <= radius * radius;
    }

    // ---------------------------------------------------------------- job items

    private boolean giveJobItem(Player player, ActiveContract contract, StepDefinition step) {
        if (player.getInventory().firstEmpty() == -1) {
            return false;
        }
        ItemStack stack = new ItemStack(step.item(), step.amount());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (step.itemName() != null && !step.itemName().isEmpty()) {
                meta.setDisplayName(Msg.color(step.itemName()));
            }
            meta.setLore(Collections.singletonList(JOB_ITEM_TAG));
            stack.setItemMeta(meta);
        }
        player.getInventory().addItem(stack);
        contract.carry(stack, step.amount());
        plugin.msg().send(player, "contract.item-given", "item", itemLabel(stack));
        return true;
    }

    private boolean removeJobItems(Player player, ActiveContract contract, int amount) {
        ItemStack template = contract.carriedItem();
        if (template == null) {
            return false;
        }
        ItemStack[] contents = player.getInventory().getStorageContents();
        int held = 0;
        for (ItemStack item : contents) {
            if (matches(item, template)) {
                held += item.getAmount();
            }
        }
        if (held < amount) {
            return false;
        }

        int remaining = amount;
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (!matches(item, template)) {
                continue;
            }
            int take = Math.min(remaining, item.getAmount());
            if (item.getAmount() - take <= 0) {
                contents[i] = null;
            } else {
                item.setAmount(item.getAmount() - take);
            }
            remaining -= take;
        }
        player.getInventory().setStorageContents(contents);
        contract.consumeCarried(amount);
        return true;
    }

    /** Removes whatever job items are left when a contract ends or is cancelled. */
    private void clearJobItems(Player player, ActiveContract contract) {
        ItemStack template = contract.carriedItem();
        if (template == null) {
            return;
        }
        ItemStack[] contents = player.getInventory().getStorageContents();
        boolean changed = false;
        for (int i = 0; i < contents.length; i++) {
            if (matches(contents[i], template)) {
                contents[i] = null;
                changed = true;
            }
        }
        if (changed) {
            player.getInventory().setStorageContents(contents);
        }
        contract.carry(null, 0);
    }

    /** Job items are matched on type + display name, so player-owned stacks are never taken. */
    private static boolean matches(ItemStack item, ItemStack template) {
        if (item == null || template == null || item.getType() != template.getType()) {
            return false;
        }
        String itemName = displayName(item);
        String templateName = displayName(template);
        if (templateName == null) {
            return itemName == null && isJobItem(item);
        }
        return templateName.equals(itemName);
    }

    private static boolean isJobItem(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasLore() && meta.getLore().contains(JOB_ITEM_TAG);
    }

    private static String displayName(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() ? meta.getDisplayName() : null;
    }

    private static String itemLabel(ItemStack item) {
        String name = displayName(item);
        return name != null ? name : item.getType().name();
    }
}
