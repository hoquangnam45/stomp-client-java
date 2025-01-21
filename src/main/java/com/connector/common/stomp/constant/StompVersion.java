package com.connector.common.stomp.constant;

public enum StompVersion
{
    // Documentation: https://stomp.github.io/stomp-specification-1.0.html
    STOMP_1_0("1.0"), // Documentation: https://stomp.github.io/stomp-specification-1.1.html
    STOMP_1_1("1.1"), // Documentation: https://stomp.github.io/stomp-specification-1.2.html
    STOMP_1_2("1.2");

    private final String version;

    StompVersion(String version)
    {
        this.version = version;
    }

    public String getVersion()
    {
        return version;
    }

    public static StompVersion parse(String version)
    {
        for (StompVersion v : values())
        {
            if (v.version.equals(version))
            {
                return v;
            }
        }
        return null;
    }
}
