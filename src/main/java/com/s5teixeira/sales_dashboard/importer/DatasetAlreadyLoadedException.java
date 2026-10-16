package com.s5teixeira.sales_dashboard.importer;

/** Thrown when someone tries to import while a dataset is already stored. Only one dataset can be active. */
public class DatasetAlreadyLoadedException extends RuntimeException {

    public DatasetAlreadyLoadedException() {
        super("A dataset is already loaded. Delete the current dataset before importing another one.");
    }
}
