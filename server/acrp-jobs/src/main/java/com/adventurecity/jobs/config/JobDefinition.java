package com.adventurecity.jobs.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/** A whole job, loaded from one YAML file in the jobs/ folder. */
public final class JobDefinition {

    private final String id;
    private final String name;
    private final Material icon;
    private final List<String> description;
    private final boolean whitelist;
    private final String dispatchChannel;
    private final List<String> dutyZones;
    private final String vehicle;
    private final String tool;
    private final int payrollIntervalMinutes;
    private final boolean payrollRequiresDuty;
    private final double payrollTaxPercent;
    private final List<JobGrade> grades;
    private final Map<String, ContractDefinition> contracts;

    private JobDefinition(String id, String name, Material icon, List<String> description, boolean whitelist,
                          String dispatchChannel, List<String> dutyZones, String vehicle, String tool,
                          int payrollIntervalMinutes, boolean payrollRequiresDuty, double payrollTaxPercent,
                          List<JobGrade> grades, Map<String, ContractDefinition> contracts) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.description = Collections.unmodifiableList(description);
        this.whitelist = whitelist;
        this.dispatchChannel = dispatchChannel;
        this.dutyZones = Collections.unmodifiableList(dutyZones);
        this.vehicle = vehicle;
        this.tool = tool;
        this.payrollIntervalMinutes = payrollIntervalMinutes;
        this.payrollRequiresDuty = payrollRequiresDuty;
        this.payrollTaxPercent = payrollTaxPercent;
        this.grades = Collections.unmodifiableList(grades);
        this.contracts = Collections.unmodifiableMap(contracts);
    }

    /** Returns null (and logs) when the file cannot produce a usable job. */
    public static JobDefinition parse(ConfigurationSection root, String fileName, Logger logger) {
        String id = root.getString("id");
        if (id == null || id.isEmpty()) {
            logger.warning("[ACRPJobs] " + fileName + " has no 'id' - file skipped.");
            return null;
        }
        id = id.toLowerCase();

        List<JobGrade> grades = new ArrayList<JobGrade>();
        int index = 1;
        for (Map<?, ?> rawGrade : root.getMapList("grades")) {
            grades.add(JobGrade.parse(toSection(rawGrade), index));
            index++;
        }
        if (grades.isEmpty()) {
            grades.add(new JobGrade(1, "موظف", 100L, 0L, 1.0D));
            logger.warning("[ACRPJobs] " + id + " has no grades - a default grade was created.");
        }
        Collections.sort(grades, new java.util.Comparator<JobGrade>() {
            @Override
            public int compare(JobGrade a, JobGrade b) {
                return Integer.compare(a.level(), b.level());
            }
        });

        Map<String, ContractDefinition> contracts = new LinkedHashMap<String, ContractDefinition>();
        for (Map<?, ?> rawContract : root.getMapList("contracts")) {
            ContractDefinition contract = ContractDefinition.parse(toSection(rawContract), id, logger);
            if (contract != null) {
                contracts.put(contract.id(), contract);
            }
        }
        if (contracts.isEmpty()) {
            logger.warning("[ACRPJobs] " + id + " has no contracts - players can only earn the duty salary.");
        }

        Material icon = Material.PAPER;
        String rawIcon = root.getString("icon");
        if (rawIcon != null && !rawIcon.isEmpty()) {
            Material matched = Material.matchMaterial(rawIcon);
            if (matched != null) {
                icon = matched;
            } else {
                logger.warning("[ACRPJobs] " + id + ": unknown icon '" + rawIcon + "'.");
            }
        }

        List<String> dutyZones = new ArrayList<String>();
        for (String zone : root.getStringList("dutyZones")) {
            dutyZones.add(zone.toLowerCase());
        }

        return new JobDefinition(
                id,
                root.getString("name", id),
                icon,
                root.getStringList("description"),
                root.getBoolean("whitelist", false),
                root.getString("dispatch", ""),
                dutyZones,
                root.getString("vehicle", ""),
                root.getString("tool", "").trim().toUpperCase(),
                Math.max(1, root.getInt("payroll.intervalMinutes", 30)),
                root.getBoolean("payroll.requiresDuty", true),
                Math.max(0.0D, Math.min(100.0D, root.getDouble("payroll.taxPercent", 0.0D))),
                grades,
                contracts);
    }

    private static ConfigurationSection toSection(Map<?, ?> raw) {
        MemoryConfiguration section = new MemoryConfiguration();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            section.set(String.valueOf(entry.getKey()), entry.getValue());
        }
        return section;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Material icon() {
        return icon;
    }

    public List<String> description() {
        return description;
    }

    public boolean whitelist() {
        return whitelist;
    }

    /** Empty when the job does not receive player calls. */
    public String dispatchChannel() {
        return dispatchChannel;
    }

    public List<String> dutyZones() {
        return dutyZones;
    }

    public String vehicle() {
        return vehicle;
    }

    /** Job tool handed out on duty and taken back off duty. Empty for jobs that need none. */
    public String tool() {
        return tool;
    }

    public int payrollIntervalMinutes() {
        return payrollIntervalMinutes;
    }

    public boolean payrollRequiresDuty() {
        return payrollRequiresDuty;
    }

    public double payrollTaxPercent() {
        return payrollTaxPercent;
    }

    public List<JobGrade> grades() {
        return grades;
    }

    public Map<String, ContractDefinition> contracts() {
        return contracts;
    }

    public ContractDefinition contract(String contractId) {
        return contractId == null ? null : contracts.get(contractId);
    }

    /** Highest grade whose xp requirement the player has met. */
    public JobGrade gradeForXp(long xp) {
        JobGrade current = grades.get(0);
        for (JobGrade grade : grades) {
            if (xp >= grade.xpRequired()) {
                current = grade;
            }
        }
        return current;
    }

    /** The grade after this one, or null when already at the top. */
    public JobGrade nextGrade(JobGrade current) {
        for (JobGrade grade : grades) {
            if (grade.level() > current.level()) {
                return grade;
            }
        }
        return null;
    }

    public JobGrade gradeByLevel(int level) {
        for (JobGrade grade : grades) {
            if (grade.level() == level) {
                return grade;
            }
        }
        return grades.get(0);
    }

    public String permissionNode() {
        return "acrp.jobs.job." + id;
    }
}
