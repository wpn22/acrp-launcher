package com.adventurecity.jobs.ui;

import com.adventurecity.jobs.ACRPJobsPlugin;
import com.adventurecity.jobs.config.ContractDefinition;
import com.adventurecity.jobs.config.JobDefinition;
import com.adventurecity.jobs.contract.ActiveContract;
import com.adventurecity.jobs.storage.PlayerData;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The contract board for the player's current job. */
public final class ContractMenu extends ChestMenu {

    private static final int[] SLOTS = { 10, 11, 12, 13, 14, 15, 16 };

    public ContractMenu(ACRPJobsPlugin plugin) {
        super(plugin, "&8المهام المتاحة", 3);
    }

    @Override
    protected void build(Player player) {
        PlayerData data = plugin.players().get(player);
        JobDefinition job = data == null ? null : plugin.jobs().get(data.currentJob());

        if (job == null) {
            set(13, item(Material.BARRIER, "&cما عندك وظيفة", Arrays.asList("&7اختر وظيفة أول.")),
                    clicked -> openLater(new JobCenterMenu(plugin), clicked));
            set(22, item(Material.BARRIER, "&cإغلاق", null), clicked -> clicked.closeInventory());
            fillEmpty();
            return;
        }

        ActiveContract running = plugin.contracts().get(player);
        int index = 0;
        for (final ContractDefinition contract : job.contracts().values()) {
            if (contract.dispatchOnly() || index >= SLOTS.length) {
                continue;
            }

            List<String> lore = new ArrayList<String>(contract.description());
            lore.add("");
            lore.add("&7الأجر: &a" + payLabel(contract));
            if (contract.payPerBlock() > 0.0D) {
                lore.add("&7+ &a" + (long) contract.payPerBlock() + " " + plugin.economy().symbol() + " &7لكل بلوك");
            }
            lore.add("&7الخبرة: &f+" + contract.xpReward());
            lore.add("&7عدد الخطوات: &f" + contract.steps().size());
            lore.add("");

            long cooldown = plugin.cooldowns().remaining(player.getUniqueId(), contract.id());
            if (running != null) {
                lore.add("&cعندك مهمة شغالة");
            } else if (!data.onDuty()) {
                lore.add("&cتحتاج تبدأ الدوام أول");
            } else if (cooldown > 0L) {
                lore.add("&cانتظر " + cooldown + " ثانية");
            } else {
                lore.add("&aاضغط لبدء المهمة");
            }

            final JobDefinition currentJob = job;
            set(SLOTS[index], item(contract.icon(), contract.name(), lore), clicked -> {
                clicked.closeInventory();
                plugin.contracts().start(clicked, currentJob, contract, null);
            });
            index++;
        }

        if (index == 0) {
            set(13, item(Material.PAPER, "&7ما فيه مهام", Arrays.asList(
                    "&7هذي الوظيفة تعتمد على طلبات اللاعبين",
                    "&7أو على الراتب الدوري فقط.")));
        }

        if (running != null) {
            set(21, item(Material.REDSTONE, "&cإلغاء المهمة الحالية", Arrays.asList(
                            "&7" + running.definition().name(),
                            "&7الخطوة: &f" + (running.index() + 1) + "/" + running.stepCount())),
                    clicked -> {
                        clicked.closeInventory();
                        plugin.contracts().cancel(clicked, true);
                    });
        }

        set(18, item(Material.BOOK, "&7رجوع", Arrays.asList("&7العودة لصفحة وظيفتي.")),
                clicked -> openLater(new MyJobMenu(plugin), clicked));
        set(22, item(Material.BARRIER, "&cإغلاق", null), clicked -> clicked.closeInventory());
        fillEmpty();
    }

    private String payLabel(ContractDefinition contract) {
        long min = contract.basePay();
        long max = contract.basePay() + contract.randomBonus();
        if (min == max) {
            return plugin.economy().format(min);
        }
        return com.adventurecity.jobs.util.Msg.number(min) + " - " + plugin.economy().format(max);
    }
}
