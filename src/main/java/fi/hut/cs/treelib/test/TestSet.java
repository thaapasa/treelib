package fi.hut.cs.treelib.test;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Required;

import fi.hut.cs.treelib.Database;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.test.Workload.Distribution;

public class TestSet<K extends Key<K>, V extends PageValue<?>> {

    private List<TestState> states;
    private List<Test> tests;
    private Map<String, Database<K, V, ?>> databases;
    private Map<String, String> databaseSettings;

    public TestSet() {
    }

    public TestSet(TestSet<K, V> origin, Distribution distribution) {
        databases = origin.databases;
        databaseSettings = origin.databaseSettings;
    }

    public Map<String, Database<K, V, ?>> getDatabases() {
        return databases;
    }

    @Required
    public void setDatabases(Map<String, Database<K, V, ?>> databases) {
        this.databases = databases;
    }

    public void setDatabaseSettings(Map<String, String> databaseSettings) {
        this.databaseSettings = databaseSettings;
    }

    public String getDatabaseSettings(String databaseKey) {
        if (databaseSettings == null)
            return null;
        return databaseSettings.get(databaseKey);
    }

    public List<TestState> getStates() {
        return states;
    }

    @Required
    public void setStates(List<TestState> states) {
        this.states = states;
    }

    public List<Test> getTests() {
        return tests;
    }

    @Required
    public void setTests(List<Test> tests) {
        this.tests = tests;
    }

}
