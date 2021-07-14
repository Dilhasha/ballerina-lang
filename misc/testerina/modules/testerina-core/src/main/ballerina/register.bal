public type TFunction record {
    boolean enable = true;
    function[] before = [];
    function[] after = [];
    function[] dependsOn = [];
    string[] groups = [];
    function test;
};

public class TestSuite {
    private map<TFunction> testFunctions = {};
    private function? beforeSuite = ();
    private function? afterSuite = ();
    private function? beforeEach = ();
    private function? afterEach = ();

    public function getTestFunctions() returns map<TFunction>{
        return self.testFunctions;
    }

    public function addTestFunction(string testName, TFunction testFunction) {
        self.testFunctions[testName] = testFunction;
    }
}
stream<string> testResultsStream = new;
Registrar registrar = new;

public function getCallerModuleName() returns string {
    error e = error("error!");
    //position 0 and 1 denote function calls in this module
    error:CallStackElement callStack = e.stackTrace().callStack[2];
    string moduleName = split(callStack.moduleName, "\\.")[1];
    return moduleName;
}

public function registerTest(string name, function f, function[] afterTests = [], function[] beforeTests = [], function[] dependsOnTests = [], string[] groups = []) returns error? {
    registrar.register(getCallerModuleName(), name, f);
}

public class Registrar {
    public map<TestSuite> testSuites = {};

    public function getTestSuites() returns map<TestSuite>{
        return self.testSuites;
    }

    public function getTestFunctions(string testSuiteName) returns map<TFunction>?{
        TestSuite? suite = self.testSuites[testSuiteName];
        if(!(suite is ())){
            return suite.getTestFunctions();
        }
    }
    
    int i = 1;

    public function register(string testSuiteName, string name, function f, function[] afterTests = [], function[] beforeTests = [], function[] dependsOnTests = [], string[] groups = []) {
        string testName = name;
        if (self.isDuplicateKey(testSuiteName, name)) {
            testName = name + self.i.toBalString();
            println("Warning: Provided test name " + name + " already exists. Renaming to " + testName + "\n");
            self.i = self.i + 1;
        }
        if (f is function () returns ()) {
            if self.testSuites.hasKey(testSuiteName) {
                TestSuite? suite = self.testSuites[testSuiteName];
                if(!(suite is ())){
                    suite.addTestFunction(testName, {test: f});
                }
            } else{
                TestSuite suite = new();
                suite.addTestFunction(testName, {test: f});
                self.testSuites[testSuiteName] = suite;
            }
        }
    }

    function isDuplicateKey(string suiteName, string testName) returns boolean {
        TestSuite? suite = self.testSuites[suiteName];
        if !(suite is ()){
            foreach string key in suite.getTestFunctions().keys() {
                if (testName == key) {
                    return true;
                }
            }
        }
        return false;
    }

}


