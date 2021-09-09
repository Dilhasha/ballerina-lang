import ballerina/io;

// Prints `Hello World`.

public function main() {
    io:println("Hello, World!");
    io:println(getStr());
}

function getStr() returns string {
    var str = "Hi";
    return <string>str;
}