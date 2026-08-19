package com.adventurecity.jobs.admin;

import com.adventurecity.jobs.ACRPJobsPlugin;
import com.adventurecity.jobs.ai.NpcPersona;
import com.adventurecity.jobs.config.ContractDefinition;
import com.adventurecity.jobs.config.JobDefinition;
import com.adventurecity.jobs.config.StepDefinition;
import com.adventurecity.jobs.config.StepType;
import com.adventurecity.jobs.route.Route;
import com.adventurecity.jobs.spot.SpotPool;
import com.adventurecity.jobs.spot.SpotRotation;
import com.adventurecity.jobs.training.TrainingLesson;
import com.adventurecity.jobs.training.TrainingPlan;
import com.adventurecity.jobs.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Everything that can be checked without a human walking the map.
 *
 * <p>The manual test script is long, and most of what it covers is really "does this piece of
 * config point at something that exists". That part is machine-checkable, so it is checked here -
 * leaving the human to test only what genuinely needs eyes: does the walker look right, does the
 * pump feel right, does the lesson land.</p>
 *
 * <p>Everything here is read-only apart from the deliberate economy round trip, which puts the
 * balance back exactly as it found it.</p>
 */
public final class SelfTest {

    private final ACRPJobsPlugin plugin;
    private final CommandSender sender;

    private int passed;
    private int failed;
    private int warned;

    public SelfTest(ACRPJobsPlugin plugin, CommandSender sender) {
        this.plugin = plugin;
        this.sender = sender;
    }

    /** @param section one of config/jobs/zones/spots/routes/training/npcs/economy, or "all" */
    public void run(String section) {
        String want = section == null || section.isEmpty() ? "all" : section.toLowerCase();
        boolean all = "all".equals(want);

        line("&8&m-----------------&r &b&lفحص النظام &r&8&m-----------------");

        if (all || "config".equals(want)) {
            section("الإعدادات");
            checkConfig();
        }
        if (all || "jobs".equals(want)) {
            section("الوظائف");
            checkJobs();
        }
        if (all || "zones".equals(want)) {
            section("المناطق");
            checkZones();
        }
        if (all || "spots".equals(want)) {
            section("نقاط العمل");
            checkSpots();
        }
        if (all || "routes".equals(want)) {
            section("المسارات");
            checkRoutes();
        }
        if (all || "training".equals(want)) {
            section("التدريب");
            checkTraining();
        }
        if (all || "npcs".equals(want)) {
            section("الشخصيات");
            checkNpcs();
        }
        if (all || "economy".equals(want)) {
            section("الاقتصاد");
            checkEconomy();
        }

        line("&8&m--------------------------------------------");
        if (failed == 0 && warned == 0) {
            line("&a&l✔ كل شي تمام &7(&a" + passed + "&7 فحص نجح)");
        } else {
            line("&7نجح: &a" + passed + "   &7تنبيه: &e" + warned + "   &7فشل: &c" + failed);
            if (failed > 0) {
                line("&cصلّح اللي فوق قبل ما تفتح السيرفر للاعبين.");
            }
        }
    }

    // ---------------------------------------------------------------- sections

    private void checkConfig() {
        check(plugin.settings() != null, "config.yml محمّل");
        check(!plugin.settings().allowBlockChanges,
                "حماية الخريطة مفعّلة (ما يتغيّر ولا بلوك)",
                "world.allowBlockChanges مفتوح - النظام يقدر يبدّل بلوكات أعمدة الإنارة");
        check(plugin.settings().maxActiveWalkers > 0,
                "سقف البوتات = " + plugin.settings().maxActiveWalkers,
                "maxActiveWalkers = 0، ما راح يظهر ولا بوت");
        check(plugin.settings().maxActiveSpotEntities > 0,
                "سقف أكياس الزبالة = " + plugin.settings().maxActiveSpotEntities,
                "maxActiveEntities = 0، ما راح يظهر ولا كيس");
        check(plugin.settings().pumpSeconds >= 1 && plugin.settings().pumpSeconds <= 30,
                "زمن رش المضخة = " + plugin.settings().pumpSeconds + "ث");
        if (plugin.settings().aiEnabled) {
            check(!plugin.settings().aiToken.isEmpty(),
                    "الذكاء الاصطناعي مفعّل ومعه توكن",
                    "ai.enabled مفعّل بس التوكن فاضي - الجسر بيرفض كل طلب");
        } else {
            info("الذكاء الاصطناعي معطّل (الشخصيات ترد بحواراتها المكتوبة)");
        }
    }

    private void checkJobs() {
        int jobs = plugin.jobs().all().size();
        check(jobs > 0, "عدد الوظائف = " + jobs, "ما فيه ولا وظيفة محمّلة");

        for (JobDefinition job : plugin.jobs().all()) {
            String tag = "&f" + job.id() + "&7";
            check(!job.grades().isEmpty(), tag + " عنده رتب");
            check(!job.contracts().isEmpty(), tag + " عنده مهام",
                    tag + " ما عنده ولا مهمة - اللاعب بياخذ راتب دوام بس");

            for (ContractDefinition contract : job.contracts().values()) {
                String what = tag + " / &f" + contract.id();
                check(!contract.steps().isEmpty(), what + " فيه خطوات");

                for (StepDefinition step : contract.steps()) {
                    if (step.type() == StepType.CLEAR_SPOTS) {
                        SpotPool pool = plugin.spots().registry().get(step.pool());
                        check(pool != null, what + " ➜ مجموعة &f" + step.pool() + "&7 موجودة",
                                what + " يحتاج مجموعة نقاط &f" + step.pool() + "&c وهي غير موجودة");
                        if (pool != null) {
                            check(pool.pointCount() >= step.amount(),
                                    what + " ➜ نقاط كافية (" + pool.pointCount() + " ≥ " + step.amount() + ")",
                                    what + " يطلب &f" + step.amount() + "&c نقطة لكن المجموعة فيها &f"
                                            + pool.pointCount() + "&c فقط - المهمة ما تخلص أبداً");
                        }
                    }
                    if (step.zone() != null && !step.zone().isEmpty()) {
                        check(plugin.zones().exists(step.zone()),
                                what + " ➜ منطقة &f" + step.zone() + "&7 موجودة",
                                what + " يحتاج منطقة &f" + step.zone() + "&c وهي غير محددة");
                    }
                    if (step.zoneGroup() != null && !step.zoneGroup().isEmpty()) {
                        check(plugin.zones().groupExists(step.zoneGroup()),
                                what + " ➜ مجموعة مناطق &f" + step.zoneGroup() + "&7 موجودة",
                                what + " يحتاج مجموعة &f" + step.zoneGroup() + "&c وهي فاضية");
                    }
                }
            }

            if (!job.dispatchChannel().isEmpty()) {
                boolean hasDispatchContract = false;
                for (ContractDefinition contract : job.contracts().values()) {
                    if (contract.dispatchOnly()) {
                        hasDispatchContract = true;
                        break;
                    }
                }
                check(hasDispatchContract,
                        tag + " قناة الطلبات &f" + job.dispatchChannel() + "&7 لها مهمة",
                        tag + " يستقبل طلبات لكن ما عنده مهمة dispatchOnly - الطلبات ما تشتغل");
            }

            if ("PUMP".equals(job.tool())) {
                boolean usesPump = false;
                for (ContractDefinition contract : job.contracts().values()) {
                    for (StepDefinition step : contract.steps()) {
                        if (step.type() != StepType.CLEAR_SPOTS) {
                            continue;
                        }
                        SpotPool pool = plugin.spots().registry().get(step.pool());
                        if (pool != null && pool.type().usesPump()) {
                            usesPump = true;
                        }
                    }
                }
                warnIf(!usesPump, tag + " ياخذ المضخة بس ما عنده شغل يحتاجها");
            }
        }
    }

    private void checkZones() {
        List<String> missing = new ArrayList<String>();
        for (String id : plugin.jobs().requiredZoneIds()) {
            if (!plugin.zones().exists(id)) {
                missing.add(id);
            }
        }
        for (String group : plugin.jobs().requiredZoneGroups()) {
            if (!plugin.zones().groupExists(group)) {
                missing.add("مجموعة " + group);
            }
        }
        check(missing.isEmpty(), "كل المناطق المطلوبة محددة (" + plugin.zones().all().size() + ")",
                "ناقص: &f" + join(missing));
    }

    private void checkSpots() {
        int pools = plugin.spots().registry().size();
        if (pools == 0) {
            warnIf(true, "ما فيه ولا مجموعة نقاط - وظائف النظافة والبستنة والكهرباء ما تشتغل");
            return;
        }
        info("عدد المجموعات = &f" + pools);

        long now = System.currentTimeMillis();
        for (SpotPool pool : plugin.spots().registry().all()) {
            String tag = "&f" + pool.id() + "&7";
            check(Bukkit.getWorld(pool.worldName()) != null,
                    tag + " عالمه &f" + pool.worldName() + "&7 محمّل",
                    tag + " يشير لعالم &f" + pool.worldName() + "&c وهو غير محمّل");
            check(pool.pointCount() > 0, tag + " فيه " + pool.pointCount() + " نقطة",
                    tag + " ما فيه ولا نقطة");

            int active = SpotRotation.countActive(pool);
            int target = Math.min(pool.activeCount(), pool.pointCount());
            check(active == target,
                    tag + " النشط " + active + "/" + target + " (دوران كل " + pool.respawnMinutes() + "د)",
                    tag + " النشط &f" + active + "&c لكن المفروض &f" + target
                            + "&c - فيه " + pool.pendingCount() + " خانة منتظرة، التالي بعد "
                            + SpotRotation.secondsUntilNext(pool, now) + "ث");
            warnIf(pool.activeCount() > pool.pointCount(),
                    tag + " activeCount (" + pool.activeCount() + ") أكبر من عدد النقاط ("
                            + pool.pointCount() + ") - بيستخدم عدد النقاط");
        }
        info("أكياس ظاهرة الآن: &f" + plugin.spots().bodyCount() + "&7/&f"
                + plugin.settings().maxActiveSpotEntities);
    }

    private void checkRoutes() {
        int routes = plugin.routes().registry().size();
        if (routes == 0) {
            warnIf(true, "ما فيه ولا مسار - المدينة بتكون فاضية والمشرفين ما يظهرون");
            return;
        }
        info("عدد المسارات = &f" + routes);

        for (Route route : plugin.routes().registry().all()) {
            String tag = "&f" + route.id() + "&7";
            check(route.path().valid(),
                    tag + " مساره صالح (" + route.cornerCount() + " زاوية)",
                    tag + " يحتاج زاويتين مختلفتين على الأقل - ما أحد بيمشي عليه");
            check(Bukkit.getWorld(route.worldName()) != null,
                    tag + " عالمه محمّل",
                    tag + " يشير لعالم &f" + route.worldName() + "&c غير محمّل");
            warnIf(route.population() == 0, tag + " عدد بوتاته صفر");

            checkPersonaExists(tag + " persona", route.persona());
            checkPersonaExists(tag + " leadPersona", route.leadPersona());
        }

        int walkers = plugin.routes().walkerCount();
        int active = plugin.routes().activeCount();
        int cap = plugin.settings().maxActiveWalkers;
        info("البوتات: &f" + walkers + "&7 إجمالي، &a" + active + "&7 ظاهر الآن (السقف &f" + cap + "&7)");
        check(active <= cap, "السقف محترم", "الظاهر &f" + active + "&c أكبر من السقف &f" + cap);
    }

    private void checkPersonaExists(String label, String personaId) {
        if (personaId == null || personaId.isEmpty()) {
            return;
        }
        check(plugin.npcs().get(personaId) != null,
                label + " ➜ &f" + personaId + "&7 موجودة",
                label + " يشير لشخصية &f" + personaId + "&c غير موجودة في npcs.yml");
    }

    private void checkTraining() {
        int withTraining = 0;
        for (JobDefinition job : plugin.jobs().all()) {
            TrainingPlan plan = job.training();
            if (plan == null) {
                warnIf(true, "&f" + job.id() + "&e ما له تدريب - اللاعب الجديد ما أحد يشرح له");
                continue;
            }
            withTraining++;
            String tag = "&f" + job.id() + "&7";

            NpcPersona trainer = plugin.npcs().get(plan.trainer());
            check(trainer != null, tag + " مشرفه &f" + plan.trainer() + "&7 موجود",
                    tag + " مشرفه &f" + plan.trainer() + "&c غير موجود في npcs.yml");
            if (trainer == null) {
                continue;
            }

            // The single thing most likely to silently break the experience: a supervisor nobody
            // can find, because they are neither walking a route nor pinned to an NPC.
            check(trainerIsReachable(plan.trainer()),
                    tag + " مشرفه موجود في المدينة",
                    tag + " المشرف &f" + Msg.plain(trainer.name())
                            + "&c ما هو على أي مسار ولا مربوط بـ NPC - اللاعب ما راح يلقاه أبداً."
                            + " الحل: &f/jobsadmin route set <مسار> leadPersona " + plan.trainer());

            check(!plan.lessons().isEmpty(), tag + " فيه " + plan.size() + " دروس");
            int index = 0;
            for (TrainingLesson lesson : plan.lessons()) {
                index++;
                check(lesson.trigger() != null, tag + " الدرس " + index + " له مُشغّل");
                check(!lesson.text().isEmpty(), tag + " الدرس " + index + " فيه كلام");
            }
            warnIf(plan.intro().isEmpty(), tag + " ما فيه intro - اللاعب ما بيعرف على مين يدوّر");
        }
        info("وظائف فيها تدريب: &f" + withTraining + "&7/&f" + plugin.jobs().all().size());
    }

    /** A trainer is findable if some route carries their persona, or an NPC is linked to them. */
    private boolean trainerIsReachable(String personaId) {
        NpcPersona persona = plugin.npcs().get(personaId);
        if (persona != null && !persona.entityName().isEmpty()) {
            return true;
        }
        for (Route route : plugin.routes().registry().all()) {
            if (personaId.equals(route.leadPersona())
                    || (personaId.equals(route.persona()) && route.population() > 0)) {
                return true;
            }
        }
        return false;
    }

    private void checkNpcs() {
        int npcs = plugin.npcs().all().size();
        check(npcs > 0, "عدد الشخصيات = " + npcs, "npcs.yml فاضي");

        Set<String> names = new HashSet<String>();
        for (NpcPersona persona : plugin.npcs().all()) {
            String tag = "&f" + persona.id() + "&7";
            warnIf(!persona.hasFallback(),
                    tag + " ما عنده ردود مكتوبة - لو وقف الذكاء الاصطناعي بيسكت");
            if (!persona.entityName().isEmpty()) {
                String plain = Msg.plain(persona.entityName()).toLowerCase();
                warnIf(!names.add(plain),
                        tag + " مربوط باسم مكرر &f" + plain + "&e - ممكن يتلخبط مع شخصية ثانية");
            }
        }

    }

    private void checkEconomy() {
        if (!(sender instanceof Player)) {
            info("فحص الاقتصاد يحتاج تكون داخل اللعبة - تخطيته");
            return;
        }
        Player player = (Player) sender;
        if (plugin.players().get(player) == null) {
            check(false, "", "بياناتك ما تحمّلت بعد - جرّب بعد ثانية");
            return;
        }

        long before = plugin.economy().balance(player.getUniqueId());
        plugin.economy().deposit(player.getUniqueId(), 1L, "selftest", "فحص النظام");
        long afterDeposit = plugin.economy().balance(player.getUniqueId());
        boolean withdrew = plugin.economy().withdraw(player.getUniqueId(), 1L, "selftest", "فحص النظام");
        long after = plugin.economy().balance(player.getUniqueId());

        check(afterDeposit == before + 1L, "الإيداع يشتغل",
                "الإيداع ما غيّر الرصيد (" + before + " ➜ " + afterDeposit + ")");
        check(withdrew && after == before, "السحب يشتغل والرصيد رجع مثل ما كان",
                "الرصيد ما رجع لأصله (" + before + " ➜ " + after + ")");
    }

    // ---------------------------------------------------------------- output

    private void check(boolean condition, String okText) {
        check(condition, okText, okText);
    }

    private void check(boolean condition, String okText, String failText) {
        if (condition) {
            passed++;
            line("  &a✔ &7" + okText);
        } else {
            failed++;
            line("  &c✘ &c" + failText);
        }
    }

    private void warnIf(boolean condition, String text) {
        if (condition) {
            warned++;
            line("  &e! &e" + text);
        }
    }

    private void info(String text) {
        line("  &8• &7" + text);
    }

    private void section(String title) {
        line("&8» &b" + title);
    }

    private void line(String text) {
        sender.sendMessage(Msg.color(text));
    }

    private static String join(List<String> values) {
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (out.length() > 0) {
                out.append("&7, &f");
            }
            out.append(value);
        }
        return out.toString();
    }
}
