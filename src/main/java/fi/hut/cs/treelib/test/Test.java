package fi.hut.cs.treelib.test;

import org.springframework.beans.factory.annotation.Required;

public class Test {

    private Workload workload;
    private String name;
    private String extra;

    public Test() {
    }

    public Workload getWorkload() {
        return workload;
    }

    @Required
    public void setWorkload(Workload workload) {
        this.workload = workload;
    }

    public String getName() {
        return name;
    }

    public String getName(TestState state) {
        return name.replace("%state", state.getName());
    }

    @Required
    public void setName(String name) {
        this.name = name;
    }

    public String getExtra() {
        return extra;
    }

    public void setExtra(String extra) {
        this.extra = extra;
    }

    @Override
    public String toString() {
        return String.format("Test %s (%s)", name, workload);
    }

}
