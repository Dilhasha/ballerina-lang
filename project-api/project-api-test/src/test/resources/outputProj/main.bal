import ballerina/io;

int count = 0;

public function main() {
    incrementedcount();
    io:println("hello " + count.toString());
}

function incrementedcount(){
    count = count + 1;
}
