// Copyright (c) 2021 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
//
// WSO2 Inc. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

// Another Test file to test the test execution behavior when @BeforeGroups and @AfterGroups functions
// are defined. The expected behavior is that, each @BeforeGroups function will execute before the first
// test of that group is executed and each @AfterGroups function will execute after the
// last test of that group is executed.

import ballerina/test;

string a = "1";

@test:BeforeGroups {value: ["g1"]}
function beforeGroupsFuncG1() {
    a += "2";
}

@test:BeforeGroups {value: ["g2"]}
function beforeGroupsFuncG2() {
    test:assertFail(msg = "Before groups function for g2 failed!");
    a += "4";
}

@test:AfterGroups { value : ["g1", "g2"] }
function afterGroupsFuncG1G2() {
    a += "56";
}

@test:AfterGroups {value: ["g2"]}
function afterGroupsFuncG2() {
    a += "9";
}

@test:AfterGroups {value: ["g2"], alwaysRun: true}
function afterGroupsFunc1G2() {
    a+="23456";
}

@test:Config {
    groups: ["g1"]
}
function testFunction1() {
    a += "3";
}

@test:Config{
    groups: ["g1", "g2"]
}
function testFailedFunction() {
    test:assertFail("Intentinally failing dopends on function.");
}

@test:Config {
    groups: ["g1", "g2"]
}
function testFunction2() {
    a += "4";
}

@test:Config {
    groups: ["g2"],
    dependsOn: [testFailedFunction, testFunction2]
}
function testFunction3() {
    a += "5";
}

@test:Config {
    groups: ["g2"],
    dependsOn: [testFunction3]
}
function testFunction4() {
    a += "8";
}

@test:AfterSuite{}
function testAfterSuite() {
    test:assertEquals(a, "123456");
}