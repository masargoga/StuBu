package com.stubu.specdriven.region;

/**
 * A region as the administrator sees it.
 *
 * @param employees how many employees work in the region
 * @param holidays  how many public holidays the region has
 * @param version   changes when the region is renamed; a rename refers to the version it started from
 */
public record RegionRow(long id, String name, long employees, long holidays, long version) {
}
