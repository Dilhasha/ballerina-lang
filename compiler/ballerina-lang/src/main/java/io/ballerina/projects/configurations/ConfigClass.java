package io.ballerina.projects.configurations;

class ConfigClass {
    public PackageName getPackageName() {
        return packageName;
    }

    public void setPackageName(PackageName packageName) {
        this.packageName = packageName;
    }

    PackageName packageName;
}

//class ModuleName {
////    public boolean isTestMode() {
////        return testMode;
////    }
////
////    public void setTestMode(Boolean testMode) {
////        this.testMode = testMode;
////    }
//
////    public String getMyVal() {
////        return myVal;
////    }
////
////    public void setMyVal(String myVal) {
////        this.myVal = myVal;
////    }
//
////    public Double getNewVal() {
////        return newVal;
////    }
////
////    public void setNewVal(Double newVal) {
////        this.newVal = newVal;
////    }
//
//    boolean testMode = false;
//
//    @JsonProperty(required = true)
//    String myVal = "hello";
//
//    Double newVal = 4.5;
//}
class PackageName {
    public ModuleName getModuleName() {
        return moduleName;
    }

    public void setModuleName(ModuleName moduleName) {
        this.moduleName = moduleName;
    }

    ModuleName moduleName = new ModuleName();
}
