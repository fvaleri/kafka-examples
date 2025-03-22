/*
 * Copyright 2020 Federico Valeri.
 * Licensed under the Apache License 2.0 (see LICENSE file).
 */
package it.fvaleri.kafka.model;

public class Search {
    int userId;
    String searchTerms;

    public Search(int userId, String searchTerms) {
        this.userId = userId;
        this.searchTerms = searchTerms;
    }

    public int getUserId() {
        return userId;
    }

    public String getSearchTerms() {
        return searchTerms;
    }
}
