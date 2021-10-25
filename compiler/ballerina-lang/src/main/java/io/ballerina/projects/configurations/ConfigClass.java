package io.ballerina.projects.configurations;

import com.fasterxml.jackson.annotation.JsonProperty;

class ConfigClass {
    public PackageName getPackageName() {
        return packageName;
    }

    public void setPackageName(PackageName packageName) {
        this.packageName = packageName;
    }

    PackageName packageName;

    class PackageName {
        public ModuleName getModuleName() {
            return moduleName;
        }

        public void setModuleName(ModuleName moduleName) {
            this.moduleName = moduleName;
        }

        ModuleName moduleName = new ModuleName();
    }

    class ModuleName{
        public boolean isTestMode() {
            return testMode;
        }

        public void setTestMode(boolean testMode) {
            this.testMode = testMode;
        }

        public String getMyVal() {
            return myVal;
        }

        public void setMyVal(String myVal) {
            this.myVal = myVal;
        }

        public double getNewVal() {
            return newVal;
        }

        public void setNewVal(double newVal) {
            this.newVal = newVal;
        }

        boolean testMode = false;

        @JsonProperty(required = true)
        String myVal = "hello";

        double newVal = 4.5;
    }
}




