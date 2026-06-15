package com.segment.segmentmetricservice.domain.user.cube;

/**
 * 범위 조건을 버킷팅하기 위한 레코드
 * @param startVal 버킷 GTE Value
 * @param endVal 버킷 LT Value
 */
public record BucketRange(int startVal, int endVal){
    @Override
    public String toString(){
        return "["+startVal+", "+(endVal == Integer.MAX_VALUE ? "∞" : endVal)+")";
    }
}