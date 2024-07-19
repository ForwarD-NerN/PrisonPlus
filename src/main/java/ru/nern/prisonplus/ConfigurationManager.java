package ru.nern.prisonplus;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Objects;
import java.util.stream.Stream;

import static ru.nern.prisonplus.PrisonPlus.config;

public class ConfigurationManager
{
    private static final String CONFIG_VERSION = FabricLoader.getInstance().getModContainer("prisonplus").get().getMetadata().getVersion().getFriendlyString();
    public static Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final File file = new File(FabricLoader.getInstance().getConfigDir().toFile(), "prisonplus_config.json");

    public static void loadConfig() {
        try {
            if (file.exists()) {
                StringBuilder contentBuilder = new StringBuilder();
                try (Stream<String> stream = Files.lines(file.toPath(), StandardCharsets.UTF_8)) {
                    stream.forEach(s -> contentBuilder.append(s).append("\n"));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                config = gson.fromJson(contentBuilder.toString(), Config.class);
            } else {
                config = new Config();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        setConfig(config);
    }

    public static void saveConfig() {
        config.lastLoadedVersion = CONFIG_VERSION;
        try {
            FileWriter fileWriter = new FileWriter(file);
            fileWriter.write(gson.toJson(getConfig()));
            fileWriter.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void onInit() {
        if(!file.exists()) {
            saveConfig();
        }else{
            loadConfig();
            if(!Objects.equals(config.lastLoadedVersion, CONFIG_VERSION)) saveConfig();
        }
    }

    public static void setConfig(Config config) {
        PrisonPlus.config = config;
    }

    public static Config getConfig() {
        return config;
    }

    public static class Config {
        public PrisonSettings prison;
        public SpecialItems items;
        public Player player;
        private String lastLoadedVersion = "";

        public static class PrisonSettings {
            public boolean allowPrisonIntersection = true;
            public boolean warnPrisonIntersection = true;
            public boolean allowCellIntersection = true;
            public boolean warnCellIntersection = true;
            public boolean allowCellOutsideOfPrison = true;
            public boolean warnCellOutsideOfPrison = true;
            public int prisonTrackingTime = 80;
            public boolean autoSubscribeToPrison = true;
            public boolean allowCellOverfill = false;
            public int policeJailRange = 128;
            public boolean policeJailBypassHandcuffs = false;
            public boolean keepPlayersInHandcuffsWhenPutInPrison = true;
            public int clientJailSyncTime = 100;
        }

        public static class SpecialItems {
            public boolean specialItemGlint = true;
            public int blindnessLevelBaton = 2;
            public int blindnessDurationBaton = 10;
            public int slownessLevelBaton = 4;
            public int slownessDurationBaton = 10;
            public int weaknessLevelBaton = 4;
            public int weaknessDurationBaton = 10;
            public int leashDetachDistance = -1;
            public int leashTeleportDistance = 15;
            public float leashHeightOffset = 1.4f;
            public int slownessLevelHandcuffs = 15;
            public int jumpBoostLevelHandcuffs = 230;
            public int handcuffsCooldown = 100;
            public int batonCooldown = 600;
            public String batonName = "§fДубинка";
            public String handcuffsName = "§fНаручники";
            public String handcuffsChestplateName = "§fНаручники";
            public String scissorsName = "§fНожницы";
            public int scissorsCooldown = 100;
            public int scissorsUnbreakingLevel = 5;
            public boolean scissorsEnabled = true;
            public boolean dropHandcuffs = false;
            public boolean consumeHandcuffs = false;
        }

        public static class Player{
            public boolean invulnerableToFallDamageWhenInHandcuffs = true;
            public boolean removeHandcuffsWhenPutInPrison = true;
        }

        public Config(){
            prison = new PrisonSettings();
            items = new SpecialItems();
            player = new Player();
        }
    }
}
