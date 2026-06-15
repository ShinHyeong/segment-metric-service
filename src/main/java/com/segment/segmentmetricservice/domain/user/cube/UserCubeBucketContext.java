package com.segment.segmentmetricservice.domain.user.cube;

import java.util.TreeMap;

public record UserCubeBucketContext(
        TreeMap<Integer, BucketRange> ageBucketMap,
        TreeMap<Integer, BucketRange> orderCountBucketMap
) {}
