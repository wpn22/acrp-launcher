package com.adventurecity.jobs.dispatch;

import com.adventurecity.jobs.ACRPJobsPlugin;
import com.adventurecity.jobs.config.ContractDefinition;
import com.adventurecity.jobs.config.JobDefinition;
import com.adventurecity.jobs.storage.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Player-to-worker requests. Today it powers taxi calls; the police/EMS 911 system in the next
 * phase is the same code with a different channel name, which is why it is generic from day one.
 */
public final class DispatchService {

    public static final String CHANNEL_TAXI = "taxi";

    private final ACRPJobsPlugin plugin;
    private final Map<Integer, DispatchCall> calls = new LinkedHashMap<Integer, DispatchCall>();
    private final Map<UUID, Integer> byCaller = new HashMap<UUID, Integer>();
    private int nextId = 1;

    public DispatchService(ACRPJobsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Creates a call and alerts every on-duty worker listening on the channel. */
    public boolean createCall(Player caller, String channel) {
        if (byCaller.containsKey(caller.getUniqueId())) {
            plugin.msg().send(caller, "taxi.already-called");
            return false;
        }
        if (calls.size() >= plugin.settings().maxOpenCalls) {
            plugin.msg().send(caller, "taxi.no-drivers");
            return false;
        }

        List<Player> workers = availableWorkers(channel, caller.getUniqueId());
        if (workers.isEmpty()) {
            plugin.msg().send(caller, "taxi.no-drivers");
            return false;
        }

        DispatchCall call = new DispatchCall(nextId++, caller.getUniqueId(), caller.getName(),
                channel, caller.getLocation());
        calls.put(Integer.valueOf(call.id()), call);
        byCaller.put(caller.getUniqueId(), Integer.valueOf(call.id()));

        for (Player worker : workers) {
            plugin.msg().send(worker, "taxi.driver-alert",
                    "player", caller.getName(),
                    "distance", distanceLabel(worker.getLocation(), call.location()),
                    "id", call.id());
            worker.playSound(worker.getLocation(), Sound.BLOCK_NOTE_PLING, 1.0F, 0.8F);
        }
        plugin.msg().send(caller, "taxi.called");
        return true;
    }

    /** A worker takes the call: their dispatch contract starts with the caller as its target. */
    public boolean accept(Player worker, int id) {
        DispatchCall call = calls.get(Integer.valueOf(id));
        if (call == null) {
            plugin.msg().send(worker, "taxi.not-found");
            return false;
        }
        if (call.taken()) {
            plugin.msg().send(worker, "taxi.taken");
            return false;
        }
        if (call.caller().equals(worker.getUniqueId())) {
            plugin.msg().send(worker, "taxi.not-found");
            return false;
        }

        PlayerData data = plugin.players().get(worker);
        if (data == null || !data.onDuty()) {
            plugin.msg().send(worker, "duty.not-on-duty");
            return false;
        }
        JobDefinition job = plugin.jobs().get(data.currentJob());
        if (job == null || !call.channel().equalsIgnoreCase(job.dispatchChannel())) {
            plugin.msg().send(worker, "taxi.not-found");
            return false;
        }
        Player caller = Bukkit.getPlayer(call.caller());
        if (caller == null || !caller.isOnline()) {
            expire(call, false);
            plugin.msg().send(worker, "taxi.not-found");
            return false;
        }

        ContractDefinition contract = dispatchContract(job);
        if (contract == null) {
            plugin.getLogger().warning("[ACRPJobs] Job " + job.id()
                    + " listens on a dispatch channel but has no contract with dispatchOnly: true.");
            plugin.msg().send(worker, "taxi.not-found");
            return false;
        }

        call.driver(worker.getUniqueId());
        if (!plugin.contracts().start(worker, job, contract, call.caller())) {
            call.driver(null);
            return false;
        }

        plugin.msg().send(worker, "taxi.accepted-driver", "player", caller.getName());
        plugin.msg().send(caller, "taxi.accepted-passenger", "player", worker.getName());
        caller.playSound(caller.getLocation(), Sound.BLOCK_NOTE_PLING, 1.0F, 1.2F);
        return true;
    }

    public void cancelCall(Player caller) {
        Integer id = byCaller.get(caller.getUniqueId());
        if (id == null) {
            plugin.msg().send(caller, "taxi.cancel-open");
            return;
        }
        DispatchCall call = calls.get(id);
        if (call != null && call.taken()) {
            Player driver = Bukkit.getPlayer(call.driver());
            if (driver != null) {
                plugin.contracts().cancel(driver, true);
            }
        }
        remove(id.intValue());
        plugin.msg().send(caller, "taxi.cancelled");
    }

    /** Called by the contract engine when a dispatch contract ends, for any reason. */
    public void releaseDriver(UUID callerId) {
        Integer id = byCaller.remove(callerId);
        if (id != null) {
            calls.remove(id);
        }
    }

    /** Drops calls nobody answered. Runs on the same timer as the contract tick. */
    public void tick() {
        int limit = plugin.settings().dispatchExpireSeconds;
        List<DispatchCall> expired = new ArrayList<DispatchCall>();
        for (DispatchCall call : calls.values()) {
            if (!call.taken() && call.ageSeconds() >= limit) {
                expired.add(call);
            }
        }
        for (DispatchCall call : expired) {
            expire(call, true);
        }
    }

    private void expire(DispatchCall call, boolean notify) {
        remove(call.id());
        if (!notify) {
            return;
        }
        Player caller = Bukkit.getPlayer(call.caller());
        if (caller != null) {
            plugin.msg().send(caller, "taxi.expired");
        }
    }

    private void remove(int id) {
        DispatchCall call = calls.remove(Integer.valueOf(id));
        if (call != null) {
            byCaller.remove(call.caller());
        }
    }

    public List<DispatchCall> open(String channel) {
        List<DispatchCall> out = new ArrayList<DispatchCall>();
        for (DispatchCall call : calls.values()) {
            if (!call.taken() && call.channel().equalsIgnoreCase(channel)) {
                out.add(call);
            }
        }
        return out;
    }

    public void clear() {
        calls.clear();
        byCaller.clear();
    }

    /** On-duty players working a job that listens on the channel and are free right now. */
    private List<Player> availableWorkers(String channel, UUID exclude) {
        List<Player> out = new ArrayList<Player>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getUniqueId().equals(exclude)) {
                continue;
            }
            PlayerData data = plugin.players().get(player);
            if (data == null || !data.onDuty() || data.currentJob() == null) {
                continue;
            }
            JobDefinition job = plugin.jobs().get(data.currentJob());
            if (job == null || !channel.equalsIgnoreCase(job.dispatchChannel())) {
                continue;
            }
            if (plugin.contracts().hasContract(player)) {
                continue;
            }
            out.add(player);
        }
        return out;
    }

    /** Convention: the job's first dispatchOnly contract is the one a call turns into. */
    private ContractDefinition dispatchContract(JobDefinition job) {
        for (ContractDefinition contract : job.contracts().values()) {
            if (contract.dispatchOnly()) {
                return contract;
            }
        }
        return null;
    }

    private static String distanceLabel(Location from, Location to) {
        if (from.getWorld() == null || to.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
            return "-";
        }
        return String.valueOf((int) from.distance(to));
    }
}
