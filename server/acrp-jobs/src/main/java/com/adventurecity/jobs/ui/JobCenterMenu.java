package com.adventurecity.jobs.ui;

import com.adventurecity.jobs.ACRPJobsPlugin;
import com.adventurecity.jobs.config.JobDefinition;
import com.adventurecity.jobs.config.JobGrade;
import com.adventurecity.jobs.storage.PlayerData;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** The hiring hall: every job, what it pays, and one click to take it. */
public final class JobCenterMenu extends ChestMenu {

    private static final int[] SLOTS = { 10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25 };

    public JobCenterMenu(ACRPJobsPlugin plugin) {
        super(plugin, "&8مركز الوظائف", 5);
    }

    @Override
    protected void build(Player player) {
        PlayerData data = plugin.players().get(player);
        int index = 0;

        for (final JobDefinition job : plugin.jobs().all()) {
            if (index >= SLOTS.length) {
                break;
            }
            boolean current = data != null && job.id().equals(data.currentJob());
            boolean hired = data != null && data.hasJob(job.id());
            boolean allowed = !job.whitelist()
                    || player.hasPermission(job.permissionNode())
                    || player.hasPermission("acrp.jobs.job.*")
                    || player.hasPermission("acrp.jobs.admin");

            List<String> lore = new ArrayList<String>(job.description());
            lore.add("");
            lore.add("&7الراتب: &f" + salaryRange(job) + " &7كل &f" + job.payrollIntervalMinutes() + " &7دقيقة");
            lore.add("&7المهام المتاحة: &f" + countPlayerContracts(job));
            if (hired) {
                JobGrade grade = plugin.jobManager().gradeOf(data, job);
                lore.add("&7رتبتك: &f" + grade.name());
            }
            lore.add("");
            if (current) {
                lore.add("&a✔ وظيفتك الحالية");
            } else if (!allowed) {
                lore.add("&cتحتاج قبول من الإدارة");
            } else if (hired) {
                lore.add("&eاضغط للعودة لهذي الوظيفة");
            } else {
                lore.add("&aاضغط للتوظيف");
            }

            set(SLOTS[index], item(job.icon(), job.name(), lore), clicked -> {
                clicked.closeInventory();
                if (plugin.jobManager().join(clicked, job)) {
                    openLater(new MyJobMenu(plugin), clicked);
                }
            });
            index++;
        }

        set(38, item(Material.BOOK, "&bوظيفتي", java.util.Arrays.asList(
                        "&7رتبتك، مهامك، والدوام.")),
                clicked -> openLater(new MyJobMenu(plugin), clicked));

        long balance = data == null ? 0L : data.balance();
        set(40, item(Material.GOLD_INGOT, "&eرصيدك", java.util.Arrays.asList(
                "&f" + plugin.economy().format(balance))));

        set(42, item(Material.BARRIER, "&cإغلاق", null), clicked -> clicked.closeInventory());
        fillEmpty();
    }

    private String salaryRange(JobDefinition job) {
        List<JobGrade> grades = job.grades();
        long min = grades.get(0).salary();
        long max = grades.get(grades.size() - 1).salary();
        if (min == max) {
            return plugin.economy().format(min);
        }
        return com.adventurecity.jobs.util.Msg.number(min) + " - " + plugin.economy().format(max);
    }

    private int countPlayerContracts(JobDefinition job) {
        int count = 0;
        for (com.adventurecity.jobs.config.ContractDefinition contract : job.contracts().values()) {
            if (!contract.dispatchOnly()) {
                count++;
            }
        }
        return count;
    }
}
