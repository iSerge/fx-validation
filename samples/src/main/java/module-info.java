module javafx.validations.samlpes {
    requires java.logging;
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.validations;

    requires aeFXActions;

    exports com.aeonium.aefxvalidationtest;
    opens com.aeonium.aefxvalidationtest;
}