package fi.hut.cs.treelib.test;

import org.springframework.beans.factory.annotation.Required;

public class TestState {

    private String name;
    private Workload workload;

    public TestState() {
    }

    public String getName() {
        return name;
    }

    @Required
    public void setName(String name) {
        this.name = name;
    }

    public Workload getWorkload() {
        return workload;
    }

    @Required
    public void setWorkload(Workload workload) {
        this.workload = workload;
    }

    @Override
    public String toString() {
        return String.format("Test state %s", name);
    }
}
