package com.erudite.env_interface;

import java.util.ArrayList;
import java.util.Map;

public record Concept(String objectName, String className, Map<String, String> attrs, ArrayList<String> behaviorNames) {

}
