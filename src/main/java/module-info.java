module javafx.validations {
    requires java.logging;

    requires javafx.controls;
    requires javafx.fxml;

    requires transitive aeFXActions;

    exports com.aeonium.javafx.validation;
    exports com.aeonium.javafx.validation.annotations;
    exports com.aeonium.javafx.validation.exceptions;
    exports com.aeonium.javafx.utils;

    opens com.aeonium.javafx.validation;
}