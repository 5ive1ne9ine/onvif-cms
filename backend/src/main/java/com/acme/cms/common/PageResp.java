package com.acme.cms.common;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class PageResp<T> implements Serializable {
    private long total;
    private long current;
    private long size;
    private List<T> records;

    public static <T> PageResp<T> of(long total, long current, long size, List<T> records) {
        PageResp<T> p = new PageResp<>();
        p.total = total;
        p.current = current;
        p.size = size;
        p.records = records;
        return p;
    }
}
