import ballerina/jballerina.java;

public isolated function split(string receiver, string delimiter) returns string[] {
    handle res = splitExternal(java:fromString(receiver), java:fromString(delimiter));
    return getBallerinaStringArray(res);
}

isolated function splitExternal(handle receiver, handle delimiter) returns handle = @java:Method {
    name: "split",
    'class: "java.lang.String",
    paramTypes: ["java.lang.String"]
} external;

isolated function getBallerinaStringArray(handle h) returns string[] = @java:Method {
    'class:"io.ballerina.runtime.api.utils.StringUtils",
    name:"fromStringArray",
    paramTypes:["[Ljava.lang.String;"]
} external;

isolated function outStream() returns handle = @java:FieldGet {
    name: "out",
    'class: "java.lang.System"
} external;

isolated function print(handle printStream, any|error obj) = @java:Method {
    name: "print",
    'class: "java.io.PrintStream",
    paramTypes: ["java.lang.Object"]
} external;

isolated function println(any|error... objs) {
    handle outStreamObj = outStream();
    foreach var obj in objs {
        print(outStreamObj, obj);
    }
    print(outStreamObj, "\n");
}
