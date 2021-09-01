//import ballerina/io;
//import ballerina/jballerina.java;
import ballerina/io;

// Prints `Hello World`.
int count = 0;

public function main() {
    incrementedcount();
    incrementedcount();
    io:println("hello " + count.toString());
}

function incrementedcount(){
    count = count + 1;
}

////Extern methods to verify no errors while testing
//function system_out() returns handle = @java:FieldGet {
//    name: "out",
//    'class: "java.lang.System"
//} external;
//
//function println(handle receiver, handle arg0) = @java:Method {
//    name: "println",
//    'class: "java.io.PrintStream",
//    paramTypes: ["java.lang.String"]
//} external;
//
//function print(string str) {
//    println(system_out(), java:fromString(str));
//}
