package com.stubu.specdriven.region;

/** There is no region with that id. */
public class RegionNotFoundException extends RuntimeException {

    public RegionNotFoundException() {
        super("Region not found");
    }
}
