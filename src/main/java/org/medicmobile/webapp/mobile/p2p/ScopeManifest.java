package org.medicmobile.webapp.mobile.p2p;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable scope manifest describing a user's P2P replication scope.
 *
 * Matches the contract in CONTRACT.md Section 3:
 * {
 *   "facility_subtree_root": "<facility_uuid>",
 *   "replication_depth": 1,
 *   "shared_doc_types": ["person", "clinic", ...],
 *   "scope_version": "2026-03-23T00:00:00Z"
 * }
 */
public final class ScopeManifest {

    private final String facilitySubtreeRoot;
    private final int replicationDepth;
    private final List<String> sharedDocTypes;
    private final String scopeVersion;

    public ScopeManifest(String facilitySubtreeRoot, int replicationDepth,
                         List<String> sharedDocTypes, String scopeVersion) {
        if (facilitySubtreeRoot == null || facilitySubtreeRoot.isEmpty()) {
            throw new IllegalArgumentException("facilitySubtreeRoot must not be null or empty");
        }
        if (replicationDepth < 0) {
            throw new IllegalArgumentException("replicationDepth must be >= 0, got: " + replicationDepth);
        }
        if (sharedDocTypes == null) {
            throw new IllegalArgumentException("sharedDocTypes must not be null");
        }
        if (scopeVersion == null || scopeVersion.isEmpty()) {
            throw new IllegalArgumentException("scopeVersion must not be null or empty");
        }

        this.facilitySubtreeRoot = facilitySubtreeRoot;
        this.replicationDepth = replicationDepth;
        this.sharedDocTypes = Collections.unmodifiableList(new ArrayList<>(sharedDocTypes));
        this.scopeVersion = scopeVersion;
    }

    /** Parse a ScopeManifest from a JSON object (CONTRACT.md Section 3 format). */
    public static ScopeManifest fromJson(JSONObject json) throws JSONException {
        Object rawFacility = json.get("facility_subtree_root");
        String facilityRoot;
        if (rawFacility instanceof JSONArray) {
            facilityRoot = ((JSONArray) rawFacility).getString(0);
        } else {
            facilityRoot = rawFacility.toString();
        }
        int depth = json.getInt("replication_depth");
        String version = json.getString("scope_version");

        JSONArray typesArray = json.getJSONArray("shared_doc_types");
        List<String> types = new ArrayList<>(typesArray.length());
        for (int i = 0; i < typesArray.length(); i++) {
            types.add(typesArray.getString(i));
        }

        return new ScopeManifest(facilityRoot, depth, types, version);
    }

    public String getFacilitySubtreeRoot() {
        return facilitySubtreeRoot;
    }

    public int getReplicationDepth() {
        return replicationDepth;
    }

    public List<String> getSharedDocTypes() {
        return sharedDocTypes;
    }

    public String getScopeVersion() {
        return scopeVersion;
    }

    @Override
    public String toString() {
        return "ScopeManifest{" +
                "facilitySubtreeRoot='" + facilitySubtreeRoot + '\'' +
                ", replicationDepth=" + replicationDepth +
                ", sharedDocTypes=" + sharedDocTypes +
                ", scopeVersion='" + scopeVersion + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScopeManifest that = (ScopeManifest) o;
        return replicationDepth == that.replicationDepth
                && facilitySubtreeRoot.equals(that.facilitySubtreeRoot)
                && sharedDocTypes.equals(that.sharedDocTypes)
                && scopeVersion.equals(that.scopeVersion);
    }

    @Override
    public int hashCode() {
        int result = facilitySubtreeRoot.hashCode();
        result = 31 * result + replicationDepth;
        result = 31 * result + sharedDocTypes.hashCode();
        result = 31 * result + scopeVersion.hashCode();
        return result;
    }
}
