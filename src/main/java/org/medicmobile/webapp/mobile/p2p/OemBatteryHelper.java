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
    public static RiskLevel getRiskLevel() {
        String manufacturer = Build.MANUFACTURER.toLowerCase();

        // Transsion brands (dominant in Africa) — most aggressive
        if (manufacturer.contains("tecno") || manufacturer.contains("infinix")
                || manufacturer.contains("itel")) {
            return RiskLevel.EXTREME;
        }

        // Major OEMs with aggressive battery management
        if (manufacturer.contains("samsung") || manufacturer.contains("huawei")
                || manufacturer.contains("xiaomi") || manufacturer.contains("oppo")
                || manufacturer.contains("vivo") || manufacturer.contains("realme")
                || manufacturer.contains("oneplus")) {
            return RiskLevel.HIGH;
        }

        // Moderate
        if (manufacturer.contains("nokia") || manufacturer.contains("motorola")
                || manufacturer.contains("lenovo")) {
            return RiskLevel.MEDIUM;
        }

        // Google Pixel, stock Android, unknown
        return RiskLevel.LOW;
    }

    /**
     * Get human-readable guidance for the user to disable battery optimization.
     * Instructions are specific to the device manufacturer.
     *
     * @return localized guidance string
     */
    public static String getGuidance() {
        String manufacturer = Build.MANUFACTURER.toLowerCase();

        if (manufacturer.contains("tecno") || manufacturer.contains("infinix")
                || manufacturer.contains("itel")) {
            return "Go to Phone Master > Battery Manager > " +
                    "tap this app > select 'Allow background activity'. " +
                    "Also: Settings > Apps > this app > Battery > Unrestricted.";
        }

        if (manufacturer.contains("samsung")) {
            return "Go to Settings > Battery and device care > Battery > " +
                    "Background usage limits > Never sleeping apps > Add this app.";
        }

        if (manufacturer.contains("huawei")) {
            return "Go to Settings > Battery > App launch > " +
                    "find this app > disable 'Manage automatically' > " +
                    "enable all three toggles (Auto-launch, Secondary launch, Run in background).";
        }

        if (manufacturer.contains("xiaomi")) {
            return "Go to Settings > Apps > Manage apps > this app > " +
                    "Battery saver > No restrictions. " +
                    "Also enable Autostart in Security app.";
        }

        if (manufacturer.contains("oppo") || manufacturer.contains("realme")) {
            return "Go to Settings > Battery > this app > " +
                    "Allow background activity. " +
                    "Also: Settings > App management > this app > Battery > Allow.";
        }

        if (manufacturer.contains("vivo")) {
            return "Go to Settings > Battery > High background power consumption > " +
                    "enable for this app.";
        }

        if (manufacturer.contains("oneplus")) {
            return "Go to Settings > Battery > Battery optimization > " +
                    "this app > Don't optimize.";
        }

        if (manufacturer.contains("nokia") || manufacturer.contains("motorola")) {
            return "Go to Settings > Apps & notifications > this app > " +
                    "Battery > Unrestricted.";
        }

        return "Go to Settings > Battery > this app > " +
                "disable battery optimization for reliable P2P sync.";
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
