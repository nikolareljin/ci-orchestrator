package io.ciorch.tests

import io.ciorch.core.Version
import spock.lang.Specification
import spock.lang.Unroll

class VersionTest extends Specification {

    def "parsing a semver string sets major, minor, patch correctly"() {
        when:
        Version v = new Version("1.2.3")

        then:
        v.major == 1
        v.minor == 2
        v.patch == "3"
        v.versionString == "1.2.3"
    }

    def "get() returns formatted version string"() {
        when:
        Version v = new Version("1.2.3")

        then:
        v.get() == "1.2.3"
    }

    def "versionString property matches the input"() {
        when:
        Version v = new Version("4.5.6")

        then:
        v.versionString == "4.5.6"
    }

    def "manual field access works like toArray"() {
        when:
        Version v = new Version("2.3.4")

        then:
        v.major == 2
        v.minor == 3
        v.patch == "4"
    }

    def "new Version(0.0.0) initialises to zero"() {
        when:
        Version v = new Version("0.0.0")

        then:
        v.major == 0
        v.minor == 0
        v.patch == "0"
    }

    def "constructor with int args sets fields correctly"() {
        when:
        Version v = new Version(3, 1, 4)

        then:
        v.major == 3
        v.minor == 1
        v.patch == "4"
        v.versionString == "3.1.4"
    }

    def "v1 == v2 compareTo returns 0"() {
        given:
        Version v1 = new Version("1.2.3")
        Version v2 = new Version("1.2.3")

        expect:
        v1.compareTo(v2) == 0
    }

    def "v1 major > v2 major compareTo returns positive"() {
        given:
        Version v1 = new Version("2.0.0")
        Version v2 = new Version("1.0.0")

        expect:
        v1.compareTo(v2) > 0
    }

    def "v1 major < v2 major compareTo returns negative"() {
        given:
        Version v1 = new Version("1.0.0")
        Version v2 = new Version("2.0.0")

        expect:
        v1.compareTo(v2) < 0
    }

    def "v1 minor > v2 minor compareTo returns positive"() {
        given:
        Version v1 = new Version("1.3.0")
        Version v2 = new Version("1.2.0")

        expect:
        v1.compareTo(v2) > 0
    }

    def "v1 minor < v2 minor compareTo returns negative"() {
        given:
        Version v1 = new Version("1.2.0")
        Version v2 = new Version("1.3.0")

        expect:
        v1.compareTo(v2) < 0
    }

    def "v1 patch > v2 patch compareTo returns positive"() {
        given:
        Version v1 = new Version("1.2.5")
        Version v2 = new Version("1.2.3")

        expect:
        v1.compareTo(v2) > 0
    }

    def "v1 patch < v2 patch compareTo returns negative"() {
        given:
        Version v1 = new Version("1.2.3")
        Version v2 = new Version("1.2.5")

        expect:
        v1.compareTo(v2) < 0
    }

    def "static compare returns 0 for equal strings"() {
        expect:
        Version.compare("1.2.3", "1.2.3") == 0
    }

    def "static compare returns positive when first has higher major"() {
        expect:
        Version.compare("2.0.0", "1.9.9") > 0
    }

    def "sort sorts version string list ascending"() {
        given:
        ArrayList<String> versions = ["2.0.0", "1.0.0", "1.5.0", "1.0.1"] as ArrayList<String>

        when:
        ArrayList<String> sorted = Version.sort(versions)

        then:
        sorted[0] == "1.0.0"
        sorted[1] == "1.0.1"
        sorted[2] == "1.5.0"
        sorted[3] == "2.0.0"
    }

    def "sort does not mutate the original list"() {
        given:
        ArrayList<String> original = ["3.0.0", "1.0.0"] as ArrayList<String>

        when:
        Version.sort(original)

        then:
        original[0] == "3.0.0"
    }

    @Unroll
    def "isVersionCorrect(#input) == #expected"() {
        expect:
        Version.isVersionCorrect(input) == expected

        where:
        input   | expected
        "1.2.3" | true
        "0.0.0" | true
        "abc"   | false
        "1.2"   | false
        "1"     | false
        "1.a.3" | false
    }

    def "isVersionCorrect with allowNonSemver accepts X.Y format"() {
        expect:
        Version.isVersionCorrect("1.2", true) == true
    }

    def "isVersionCorrect with allowNonSemver accepts X format"() {
        expect:
        Version.isVersionCorrect("1", true) == true
    }

    def "increment place 1 bumps major"() {
        given:
        Version v = new Version("1.2.3")

        when:
        v.increment(1)

        then:
        v.major == 2
        v.versionString == "2.2.3"
    }

    def "increment place 2 bumps minor"() {
        given:
        Version v = new Version("1.2.3")

        when:
        v.increment(2)

        then:
        v.minor == 3
        v.versionString == "1.3.3"
    }

    def "increment place 3 bumps patch"() {
        given:
        Version v = new Version("1.2.3")

        when:
        v.increment(3)

        then:
        v.patch == "4"
        v.versionString == "1.2.4"
    }

    def "set from string updates all fields"() {
        given:
        Version v = new Version("1.0.0")

        when:
        v.set("2.5.7")

        then:
        v.major == 2
        v.minor == 5
        v.patch == "7"
    }

    def "invalid version string results in 0.0.0"() {
        when:
        Version v = new Version("abc")

        then:
        v.major == 0
        v.minor == 0
        v.patch == "0"
    }

    def "version string with v prefix is stripped"() {
        when:
        Version v = new Version("v1.2.3")

        then:
        v.major == 1
        v.minor == 2
        v.patch == "3"
    }
}
