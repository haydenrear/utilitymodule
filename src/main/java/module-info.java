module utility {
    requires jdk.incubator.vector;
    requires datavec.api;
    requires nd4j.api;
    requires reactor.core;
    requires lombok;
    requires nd4j.common;
    opens com.hayden.utilitymodule.scaling to lombok;
    opens com.hayden.utilitymodule to lombok;
}