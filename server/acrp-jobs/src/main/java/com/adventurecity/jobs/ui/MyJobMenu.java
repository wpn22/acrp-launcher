package com.adventurecity.jobs.ui;

import com.adventurecity.jobs.config.JobDefinition;
import com.adventurecity.jobs.config.JobGrade;
import com.adventurecity.jobs.ACRPJobsPlugin;
import com.adventurecity.jobs.storage.JobProgress;
import com.adventurecity.jobs.storage.PlayerData;
import com.adventurecity.jobs.storage.PlayerDataManager;
import com.adventurecity.jobs.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Rank, progress, duty toggle and the way into the contract list. */
public final class MyJobMenu extends ChestMenu {

    public MyJobMenu(ACRPJobsPlugin plugin) {
        super(plugin, "&8وظيفتي", 3);
    }

    @Override
    protected void build(Player player) {
        PlayerData data = plugin.players().get(player);
        JobDefinition job = data == null ? null : plugin.jobs().get(data.currentJob());

        if (job == null) {
            set(13, item(Material.PAPER, "&cما عندك وظيفة", Arrays.asList(
                            "&7اضغط لفتح مركز الوظائف واختر وظيفتك.")),
                    clicked -> openLater(new JobCenterMenu(plugin), clicked));
            set(22, item(Material.BARRIER, "&cإغلاق", null), clicked -> clicked.closeInventory());
            fillEmpty();
            return;
        }

        JobProgress progress = data.job(job.id());
        JobGrade grade = plugin.jobManager().gradeOf(data, job);
        JobGrade next = job.nextGrade(grade);

        List<String> lore = new ArrayList<String>();
        lore.add("&7رتبتك: &f" + grade.name());
        lore.add("&7الراتب: &f" + plugin.economy().format(grade.salary())
                + " &7كل &f" + job.payrollIntervalMinutes() + " &7دقيقة");
        lore.add("&7مضاعف الأجر: &f×" + grade.payMultiplier());
        lore.add("");
        if (next == null) {
            lore.add("&6أعلى رتبة في الوظيفة");
        } else {
            long current = progress == null ? 0L : progress.xp();
            lore.add("&7الترقية القادمة: &f" + next.name());
            lore.add("&7الخبرة: &f" + Msg.number(current) + "&7/&f" + Msg.number(next.xpRequired()));
            lore.add(progressBar(current, next.xpRequired()));
        }
        lore.add("");
        lore.add("&7أرباح اليوم: &a" + plugin.economy().format(
                data.earnedToday(PlayerDataManager.epochDay())));
        set(4, item(job.icon(), job.name(), lore));

        boolean onDuty = data.onDuty();
        set(11, item(onDuty ? Material.REDSTONE_BLOCK : Material.EMERALD_BLOCK,
                        onDuty ? "&cإنهاء الدوام" : "&aبدء الدوام",
                        onDuty
                                ? Arrays.asList("&7راتبك يتوقف والمهام تُغلق.")
                                : Arrays.asList("&7لازم تكون داخل مقر العمل.", "&7الراتب يبدأ من لحظة الدوام.")),
                clicked -> {
                    clicked.closeInventory();
                    plugin.duty().toggle(clicked);
                });

        set(13, item(Material.COMPASS, "&bالمهام المتاحة", Arrays.asList(
                        "&7اختر مهمة وابدأ العمل.",
                        onDuty ? "&a✔ أنت على الدوام" : "&cتحتاج تبدأ الدوام أول")),
                clicked -> openLater(new ContractMenu(plugin), clicked));

        set(15, item(Material.PAPER, "&eترك الوظيفة", Arrays.asList(
                        "&7توقف عن العمل بهذي الوظيفة.",
                        "&7رتبتك وخبرتك تبقى محفوظة.")),
                clicked -> {
                    clicked.closeInventory();
                    plugin.jobManager().leave(clicked);
                });

        set(18, item(Material.BOOK, "&7مركز الوظائف", Arrays.asList("&7رجوع لقائمة الوظائف.")),
                clicked -> openLater(new JobCenterMenu(plugin), clicked));
        set(22, item(Material.BARRIER, "&cإغلاق", null), clicked -> clicked.closeInventory());
        fillEmpty();
    }

    private String progressBar(long current, long needed) {
        int total = 20;
        int filled = needed <= 0L ? total : (int) Math.min(total, (current * total) / needed);
        StringBuilder bar = new StringBuilder("&a");
        for (int i = 0; i < total; i++) {
            if (i == filled) {
                bar.append("&7");
            }
            bar.append('|');
        }
        return bar.toString();
    }
}
