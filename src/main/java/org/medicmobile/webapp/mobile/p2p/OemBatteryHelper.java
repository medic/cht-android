package org.medicmobile.webapp.mobile.p2p;

import android.os.Build;

/**
 * Provides per-OEM guidance for battery optimization settings.
 *
 * Android OEMs (especially popular in Africa: Tecno, Infinix, Samsung)
 * aggressively kill background services. Users must whitelist the app
 * for reliable P2P sync.
 *
 * See RFC Section 14.4 for the OEM Kill Matrix.
 */
public class OemBatteryHelper {

    public enum RiskLevel {
        /** Google Pixel, stock Android — minimal intervention needed. */
        LOW,
        /** Nokia — moderate battery optimization. */
        MEDIUM,
        /** Samsung, Huawei, Xiaomi — aggressive battery management. */
        HIGH,
        /** Tecno, Infinix (Transsion) — extremely aggressive, kills services fast. */
        EXTREME
    }

    private OemBatteryHelper() {
        // Static utility class
    }

    /**
     * Get battery kill risk level for the current device manufacturer.
     *
     * @return the risk level based on known OEM behavior
     */
    private static final String[][] EXTREME_BRANDS = { {"tecno"}, {"infinix"}, {"itel"} };
    private static final String[][] HIGH_BRANDS = {
        {"samsung"}, {"huawei"}, {"xiaomi"}, {"oppo"}, {"vivo"}, {"realme"}, {"oneplus"}
    };
    private static final String[][] MEDIUM_BRANDS = { {"nokia"}, {"motorola"}, {"lenovo"} };

    public static RiskLevel getRiskLevel() {
        String manufacturer = Build.MANUFACTURER.toLowerCase();

        if (matchesBrand(manufacturer, EXTREME_BRANDS)) {
            return RiskLevel.EXTREME;
        }
        if (matchesBrand(manufacturer, HIGH_BRANDS)) {
            return RiskLevel.HIGH;
        }
        if (matchesBrand(manufacturer, MEDIUM_BRANDS)) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.LOW;
    }

    private static boolean matchesBrand(String manufacturer, String[][] brands) {
        for (String[] brand : brands) {
            if (manufacturer.contains(brand[0])) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get human-readable guidance for the user to disable battery optimization.
     * Instructions are specific to the device manufacturer.
     *
     * @return localized guidance string
     */
    public static String getGuidance() {
        String manufacturer = Build.MANUFACTURER.toLowerCase();
        return getGuidanceForManufacturer(manufacturer);
    }

    private static String getGuidanceForManufacturer(String manufacturer) {
        if (matchesBrand(manufacturer, EXTREME_BRANDS)) {
            return "Go to Phone Master > Battery Manager > " +
                    "tap this app > select 'Allow background activity'. " +
                    "Also: Settings > Apps > this app > Battery > Unrestricted.";
        }

        String specific = getSpecificBrandGuidance(manufacturer);
        if (specific != null) {
            return specific;
        }

        if (matchesBrand(manufacturer, MEDIUM_BRANDS)) {
            return "Go to Settings > Apps & notifications > this app > " +
                    "Battery > Unrestricted.";
        }

        return "Go to Settings > Battery > this app > " +
                "disable battery optimization for reliable P2P sync.";
    }

    private static final String[][] BRAND_GUIDANCE = {
        {"samsung", "Go to Settings > Battery and device care > Battery > "
                + "Background usage limits > Never sleeping apps > Add this app."},
        {"huawei", "Go to Settings > Battery > App launch > "
                + "find this app > disable 'Manage automatically' > "
                + "enable all three toggles (Auto-launch, Secondary launch, Run in background)."},
        {"xiaomi", "Go to Settings > Apps > Manage apps > this app > "
                + "Battery saver > No restrictions. "
                + "Also enable Autostart in Security app."},
        {"oppo", "Go to Settings > Battery > this app > "
                + "Allow background activity. "
                + "Also: Settings > App management > this app > Battery > Allow."},
        {"realme", "Go to Settings > Battery > this app > "
                + "Allow background activity. "
                + "Also: Settings > App management > this app > Battery > Allow."},
        {"vivo", "Go to Settings > Battery > High background power consumption > "
                + "enable for this app."},
        {"oneplus", "Go to Settings > Battery > Battery optimization > "
                + "this app > Don't optimize."},
    };

    private static String getSpecificBrandGuidance(String manufacturer) {
        for (String[] entry : BRAND_GUIDANCE) {
            if (manufacturer.contains(entry[0])) {
                return entry[1];
            }
        }
        return null;
    }

    /**
     * Check if the app needs battery optimization whitelisting for reliable P2P.
     *
     * @return true if the OEM is known to aggressively kill background services
     */
    public static boolean needsBatteryGuidance() {
        return getRiskLevel() != RiskLevel.LOW;
    }

    /**
     * Get the device manufacturer name (for telemetry/logging).
     *
     * @return lowercase manufacturer string
     */
    public static String getManufacturer() {
        return Build.MANUFACTURER.toLowerCase();
    }

    /**
     * Get the device model (for telemetry/logging).
     *
     * @return device model string
     */
    public static String getModel() {
        return Build.MODEL;
    }
}
