package io.ballerina.projects.configurations;

public class ConfigValueConverter {

//    TomlTransformer tomlTransformer = new TomlTransformer();
//
//    public TomlValueNode convertValue(AnyType anyValue, Type type) {
//        if (isSimpleType(type.getTag())) {
//            return createPrimitiveValue(anyValue, type);
//        }
//        return createStructuredValue(tomlValue, type);
//    }
//
//    static boolean isSimpleType(int typeTag) {
//        return typeTag <= TypeTags.BOOLEAN_TAG;
//    }
//
//    private TomlValueNode createPrimitiveValue(AnyType anyValue, Type type) {
//        TomlValueNode value = ((TomlKeyValueNode) tomlValue).value();
//        return createTomlValue(type, value);
//    }
//
//
//    private TomlValueNode createTomlValue(Type type, TomlValueNode tomlValueNode) {
//        Object tomlValue = ((TomlBasicValueNode<?>) tomlValueNode).getValue();
//        switch (type.getTag()) {
//            case TypeTags.BYTE_TAG:
//                return
//            case TypeTags.DECIMAL_TAG:
//                return ValueCreator.createDecimalValue(BigDecimal.valueOf((Double) tomlValue));
//            case TypeTags.STRING_TAG:
//                return StringUtils.fromString((String) tomlValue);
//            case TypeTags.XML_ATTRIBUTES_TAG:
//            case TypeTags.XML_COMMENT_TAG:
//            case TypeTags.XML_ELEMENT_TAG:
//            case TypeTags.XML_PI_TAG:
//            case TypeTags.XML_TAG:
//            case TypeTags.XML_TEXT_TAG:
//                return createReadOnlyXmlValue((String) tomlValue);
//            default:
//                return tomlValue;
//        }
//    }


}
