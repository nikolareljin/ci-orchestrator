#!groovy
package io.ciorch.core

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.io.Serializable

/**
 * Semantic version number class.
 *
 * Supports X.Y.Z (semver) and optionally X.Y or X (non-semver) formats.
 * Provides construction, comparison, increment, sort, and validation utilities.
 */
class Version implements Serializable {
    public int major
    public int minor
    public String patch

    /** Stores the raw format provided to the Version class. */
    public String versionString

    /**
     * When true, X, X.Y, and X.Y.Z formats are all accepted.
     * If the string does not match semver, it is treated as non-semver.
     */
    public boolean allowNonSemver = false

    /** Optional reference to the Jenkins job (used for logging). */
    public def job = null

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** Default constructor with reference to Jenkins job. Initialises to 0.0.0. */
    Version(def job) {
        this.job = job
        this.major = 0
        this.minor = 0
        this.patch = "0"
        this.versionString = "0.0.0"
    }

    /** Constructor with job reference and explicit major/minor/patch values. */
    Version(def job, int major, int minor, String patch) {
        this.job = job
        this.major = major
        this.minor = minor
        this.patch = patch
        this.versionString = "${this.major}.${this.minor}.${this.patch}" as String
    }

    /** Constructor without job reference, using integer major/minor/patch values. */
    Version(int major, int minor, int patch) {
        this.major = major
        this.minor = minor
        this.patch = patch.toString()
        this.versionString = "${this.major}.${this.minor}.${this.patch}" as String
    }

    /** Constructor that parses a version string (no job reference). */
    Version(String versionText) {
        this.versionString = versionText
        _parseVersionText(versionText, null)
    }

    /** Constructor that parses a version string with a job reference for logging. */
    Version(def job, String versionText) {
        this.job = job
        this.versionString = versionText
        _parseVersionText(versionText, job)
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void _parseVersionText(String versionText, def jobRef) {
        String text = versionText

        if (null != text && "" != text) {
            if (text.contains('v') || text.contains('V')) {
                text = text.replace('v', '').replace('V', '')
            }

            String[] values = (text instanceof String && null != text) ? text.split(/\./) : new String[0]

            int maj = 0
            int min = 0
            String pat = "0"
            String val
            int intVal

            int count = 0
            for (String v : values) {
                val = v.trim()
                if (!val.isInteger()) {
                    if (jobRef) {
                        jobRef.echo "Error with the version number!"
                    }
                    maj = 0
                    min = 0
                    pat = "0"
                    break
                }
                switch (count) {
                    case 0:
                        maj = val.toInteger()
                        break
                    case 1:
                        min = val.toInteger()
                        break
                    case 2:
                        pat = v.trim()
                        break
                }
                count++
            }

            this.major = maj
            this.minor = min
            this.patch = pat
        } else {
            if (jobRef) {
                jobRef.echo "error assigning the Version"
            }
            this.major = 0
            this.minor = 0
            this.patch = "0"
        }
    }

    // -------------------------------------------------------------------------
    // Public methods
    // -------------------------------------------------------------------------

    /**
     * Allow (or disallow) non-semver format.
     * @param allowNonSemver true to permit X and X.Y formats in addition to X.Y.Z
     */
    def setNonSemver(boolean allowNonSemver = false) {
        this.allowNonSemver = allowNonSemver
    }

    /**
     * Returns the string value of the version.
     * When allowNonSemver is set (or readNonSemVer is passed), returns the raw versionString.
     * @param readNonSemVer override flag — forces non-semver read for this call
     * @return version string
     */
    String get(boolean readNonSemVer = false) {
        if (this.allowNonSemver || readNonSemVer) {
            return this.versionString
        }
        return "${this.major}.${this.minor}.${this.patch}".toString()
    }

    /**
     * Set version from explicit int/String components.
     */
    def set(int major, int minor, String patch) {
        this.major = major
        this.minor = minor
        this.patch = patch
        this.versionString = "${this.major}.${this.minor}.${this.patch}" as String
    }

    /**
     * Set version from explicit int components.
     */
    def set(int major, int minor, int patch) {
        this.major = major
        this.minor = minor
        this.patch = patch.toString()
        this.versionString = "${this.major}.${this.minor}.${this.patch}" as String
    }

    /**
     * Set version from a version string.
     * @param versionText version string (e.g. "1.2.3" or "v1.2.3")
     * @param job optional job reference for logging
     */
    def set(String versionText, def job = null) {
        this.versionString = versionText
        _parseVersionText(versionText, job ?: this.job)
    }

    /**
     * Increment a version component in place.
     * @param place 1 = major, 2 = minor, 3 = patch
     */
    def increment(int place) {
        int placeVal = (int) Math.abs(place)
        switch (placeVal) {
            case 1:
                this.major = this.major + 1
                break
            case 2:
                this.minor = this.minor + 1
                break
            case 3:
                String patchValue = this.patch as String
                String tmp = Version.getCleanNumber(patchValue)
                int patchNumber = tmp.toInteger()
                patchNumber = patchNumber + 1
                this.patch = patchNumber.toString()
                break
            default:
                break
        }
        this.versionString = "${this.major}.${this.minor}.${this.patch}" as String
    }

    // -------------------------------------------------------------------------
    // Static utilities
    // -------------------------------------------------------------------------

    /**
     * Strip common pre-release and build suffixes from a version token.
     * Removes: v/V, -RC, -built, -src, _, -alpha, -beta, -preAlpha, -dev
     * @param patch raw patch or full version string
     * @return numeric-only string
     */
    static String getCleanNumber(String patch) {
        String clean = patch
        clean = clean.replaceAll('v', '')
        clean = clean.replaceAll('-RC', '')
        clean = clean.replaceAll('-built', '')
        clean = clean.replaceAll('-src', '')
        clean = clean.replaceAll('_', '')
        clean = clean.replaceAll('-alpha', '')
        clean = clean.replaceAll('-beta', '')
        clean = clean.replaceAll('-preAlpha', '')
        clean = clean.replaceAll('-dev', '')
        return clean
    }

    /**
     * Compare two version strings.
     *
     * Returns:
     *   0   : version1 == version2
     *  +1   : version1 major > version2 major
     *  -1   : version1 major < version2 major
     *  +2/-2: minor difference
     *  +3/-3: patch difference
     *
     * @param version1 first version string
     * @param version2 second version string
     * @return comparison result code
     */
    static int compare(String version1, String version2) {
        Version v1 = new Version(Version.getCleanNumber(version1))
        Version v2 = new Version(Version.getCleanNumber(version2))

        int patchVal_v1 = v1.patch as int
        int patchVal_v2 = v2.patch as int

        if (v1.major == v2.major && v1.minor == v2.minor && v1.patch == v2.patch) {
            return 0
        }

        if (v1.major > v2.major) {
            return 1
        } else if (v2.major > v1.major) {
            return -1
        } else if (v1.minor > v2.minor) {
            return 2
        } else if (v2.minor > v1.minor) {
            return -2
        } else {
            if (null != patchVal_v1 && null != patchVal_v2) {
                return patchVal_v1 > patchVal_v2 ? 3 : -3
            } else {
                return v1.patch > v2.patch ? 3 : -3
            }
        }
    }

    /**
     * Instance-level comparison against another Version.
     * Uses the same sign convention as {@link #compare(String, String)}.
     * @param el version to compare against
     * @return positive if this > el, negative if this < el, 0 if equal
     */
    int compareTo(Version el) {
        int patchVal_this = Version.getCleanNumber(this.patch) as int
        int patchVal_el   = Version.getCleanNumber(el.patch) as int

        int resultValue = 0

        if (this.major == el.major && this.minor == el.minor && this.patch == el.patch) {
            resultValue = 0
        } else if (this.major > el.major) {
            resultValue = 1
        } else if (el.major > this.major) {
            resultValue = -1
        } else if (this.minor > el.minor) {
            resultValue = 2
        } else if (el.minor > this.minor) {
            resultValue = -2
        } else {
            if (null != patchVal_this && null != patchVal_el) {
                resultValue = patchVal_this > patchVal_el ? 3 : -3
            } else {
                resultValue = this.patch > el.patch ? 3 : -3
            }
        }

        return resultValue
    }

    /**
     * Validate that a version string matches the expected format.
     *
     * With allowNonSemver=false (default), only X.Y.Z is accepted.
     * With allowNonSemver=true, X and X.Y are also accepted.
     *
     * @param versionValue version string to validate
     * @param allowNonSemver whether to permit non-semver formats
     * @return true if the version string is valid
     */
    static boolean isVersionCorrect(String versionValue, boolean allowNonSemver = false) {
        boolean isFound = false

        String version = Version.getCleanNumber(versionValue)

        // X.Y.Z format
        def parser = /(?<major>\d+).(?<minor>\d+).(?<patch>\d+)/
        def match = version =~ parser
        if (match.matches()) {
            isFound = true
        }

        if (allowNonSemver) {
            // X.Y format
            parser = /(?<major>\d+).(?<minor>\d+)/
            match = version =~ parser
            if (match.matches() && !isFound) {
                isFound = true
            }

            // X format
            parser = /(?<major>\d+)/
            match = version =~ parser
            if (match.matches() && !isFound) {
                isFound = true
            }
        }

        return isFound
    }

    /**
     * Sort an array of version strings in ascending order.
     * @param startArray list of version strings
     * @return sorted list (ascending)
     */
    public static ArrayList sort(ArrayList<String> startArray) {
        ArrayList<String> list = new ArrayList<>(startArray)
        int count = list.size()
        for (int i = 0; i < count - 1; i++) {
            int minIdx = i
            for (int j = i + 1; j < count; j++) {
                if (Version.compare(list[j], list[minIdx]) < 0) {
                    minIdx = j
                }
            }
            if (minIdx != i) {
                String tmp = list[i]
                list[i] = list[minIdx]
                list[minIdx] = tmp
            }
        }
        return list
    }
}
