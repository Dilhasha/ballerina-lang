public class Executer {

    private function startTest(function () returns ()  f) returns future<error?> {
        return start f();
    }

    public function execute() returns map<future<error?>>|error {
        println("Executing tests\n\n");
        map<future<error?>> testWorkers = {};
        map<TestSuite> testSuites = registrar.getTestSuites();
        foreach string item in testSuites.keys() {
            TestSuite? suite = testSuites[item];
            if(!(suite is ())){
                map<TFunction> testFunctions = suite.getTestFunctions();   
                foreach string testName in testFunctions.keys() {
                    function testFunc = testFunctions.get(testName).test;
                    if (testFunc is function () returns ()) {
                        future<error?> startTestResult = self.startTest(testFunc);
                        //Add the start time to a map od startTimes
                        testWorkers[testName] = startTestResult;
                    }
                }
            }
        }
        return testWorkers;
    }

}
