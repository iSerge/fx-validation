/*
 * Copyright (C) 2014 Robert Rohm &lt;r.rohm@aeonium-systems.de&gt;.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package com.aeonium.javafx.validation;

import com.aeonium.javafx.actions.FXActionManager;
import com.aeonium.javafx.actions.annotations.AnnotationHandler;
import com.aeonium.javafx.utils.LabelService;
import com.aeonium.javafx.validation.annotations.FXNotNull;
import com.aeonium.javafx.validation.annotations.FXNumber;
import com.aeonium.javafx.validation.annotations.FXRequired;
import com.aeonium.javafx.validation.annotations.FXString;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.beans.value.ObservableValue;
import javafx.event.EventType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyEvent;

/**
 * Handler for FXRequired, FXString validation annotations: it by default also
 * appends an asterisk postfix to all Labels of constrained controls.
 *
 *
 * @author Robert Rohm &lt;r.rohm@aeonium-systems.de&gt;
 */
public class DefaultFXValidationHandler1 implements AnnotationHandler<Annotation> {

  private FXActionManager manager;

  /**
   * The postfix for the labels of constrained fields.
   */
  private String postfix = "*";

  public DefaultFXValidationHandler1() {
  }

  DefaultFXValidationHandler1(FXActionManager manager) {
    this.manager = manager;
  }

  /**
   * The default validation handling method takes a controller object, a field
   * and the annotation, and does the following things:
   *
   * <ol>
   * <li>Query the actual control (the field value) from the controller.</li>
   * <li>Get the validation handler ("validator") from the annotation of the
   * field, then create an instance of the validation handler.</li>
   * <li>Register validator with the control in the ValidatorService</li>
   * <li>Register controller with the control in the ValidatorService</li>
   * <li>Add event listeners that call the validate() method of the validation
   * handler.</li>
   * </ol>
   *
   * @param controller
   * @param field
   * @param validation The annotation - currently not used here.
   */
  @Override
  public void handle(Object controller, Field field, Annotation validation) {
    String name = null;

    try {
      name = this.getClassName(validation);
      if (name == null) {
        return;
      }

      Control control = (Control) field.get(controller);
      List<Label> labelsFor = LabelService.getLabelsFor(control);
      if (labelsFor != null) {
        for (Label label : labelsFor) {
          String text = label.getText();
          if (!text.endsWith(this.postfix)) {
            label.setText(text.concat(this.postfix));
          }
        }
      }

      FXAbstractValidator validator = (FXAbstractValidator) Class.forName(name).newInstance();
      validator.setAnnotation(validation);
      validator.setControl(control);

      // Registering control and validator - necessary for later lookups
      ValidatorService.registerValidator(control, validator);

      // Registering control and controller - necessary for later binding
      ValidatorService.registerValidatedControl(controller, control);

      List<EventType> eventTypes = validator.getEventTypes();

      // TODO cleanup - do some actions belong outside the loop?!
      for (EventType eventType : eventTypes) {

        control.disabledProperty().addListener((ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) -> {
          doValidate(validator, control, validation);
        });

        control.focusedProperty().addListener((ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) -> {
          // if control aquires focus: get out, only validate if focus lost.
          if (newValue == true) {
            return;
          }
          doValidate(validator, control, validation);
        });

        // Text-Input: use change events and focus changes
        if (control instanceof TextInputControl) {
          TextInputControl textInputControl = (TextInputControl) control;

          textInputControl.textProperty().addListener((ObservableValue<? extends String> observable, String oldValue, String newValue) -> {
            doValidate(validator, control, validation);
          });

        } else if (control instanceof ChoiceBox) {
          ChoiceBox choiceBox = (ChoiceBox) control;
          choiceBox.valueProperty().addListener((ObservableValue observable, Object oldValue, Object newValue) -> {
            doValidate(validator, control, validation);
          });
          choiceBox.showingProperty().addListener((ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) -> {
            if (!newValue) {
              doValidate(validator, control, validation);
            }
          });

        } else if (control instanceof ComboBoxBase) {
          ComboBoxBase c = (ComboBoxBase) control;
          c.valueProperty().addListener((ObservableValue observable, Object oldValue, Object newValue) -> {
            doValidate(validator, control, validation);
          });
          c.showingProperty().addListener((ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) -> {
            if (!newValue) {
              doValidate(validator, control, validation);
            }
          });

        } else {
          // not a text input control: use key events from handlers list
          // TODO: validate concept - is this really reasonable/needed?
          control.addEventHandler(eventType, (KeyEvent event) -> {
            doValidate(validator, control, validation);
          });
        }

      }

      // pre-set validation to OK for disabled controls:
      if (control.isDisabled()) {
        doValidate(validator, control, validation);
      }

    } catch (ClassNotFoundException | IllegalArgumentException | IllegalAccessException | InstantiationException ex) {
      Logger.getLogger(DefaultFXValidationHandler1.class.getName()).log(Level.SEVERE, null, ex);
    }
  }

  /**
   * Mark the given control according to the validation success as validated or
   * failed. Currently, this is done by adding or removing stye classes.
   *
   * @param control
   * @param valid
   * @param errormessage
   */
  public static void mark(Control control, boolean valid, String errormessage) {
    if (valid) {
      if (control.getStyleClass().contains(ValidatorService.AEFX_VALIDATION_ERROR)) {
        control.getStyleClass().remove(ValidatorService.AEFX_VALIDATION_ERROR);
      }

      List<Label> labels = LabelService.getLabelsFor(control);
      for (Label label : labels) {
        if (label.getStyleClass().contains(ValidatorService.AEFX_VALIDATION_MSG)) {
          label.setVisible(false);
          label.setManaged(false);
        } else {
          label.getStyleClass().remove(ValidatorService.AEFX_VALIDATION_ERROR);
        }
      }
    } else {
      if (!control.getStyleClass().contains(ValidatorService.AEFX_VALIDATION_ERROR)) {
        control.getStyleClass().add(ValidatorService.AEFX_VALIDATION_ERROR);
      }

      List<Label> labels = LabelService.getLabelsFor(control);
      for (Label label : labels) {
//        if (label != null) {
        if (errormessage != null && label.getStyleClass().contains(ValidatorService.AEFX_VALIDATION_MSG)) {
          label.setVisible(true);
          label.setManaged(true);
          label.setText(errormessage);
        } else {
          if (!label.getStyleClass().contains(ValidatorService.AEFX_VALIDATION_ERROR)) {
            label.getStyleClass().add(ValidatorService.AEFX_VALIDATION_ERROR);
          }
        }
//        }
      }
      //            Logger.getLogger(DefaultFXValidationHandler.class.getName()).log(Level.SEVERE, null, ex);
    }
  }

  /**
   * Trigger the actual validation of a control with a given validator in the
   * way described in the fxValidation annotation.
   *
   * @param validator
   * @param control
   * @param fxValidation
   */
  private void doValidate(FXAbstractValidator validator, Control control, Annotation fxValidation) {
    System.out.println("doValidate " + control);
    try {
      validator.validate(control, fxValidation);
      mark(control, true, null);
//      ValidatorService.hideHint(control, fxValidation);
    } catch (Exception ex) {
      String message = ex.getMessage();
      if (ValidatorService.getBundle() != null) {
        ResourceBundle bundle = ValidatorService.getBundle();
        if (bundle.containsKey(message)) {
          message = bundle.getString(message);
        }
      }
      mark(control, false, message);
//      ValidatorService.showHint(control, fxValidation);
    }
  }

  public String getPostfix() {
    return postfix;
  }

  public void setPostfix(String postfix) {
    this.postfix = postfix;
  }

  private String getClassName(Annotation validation) throws IllegalAccessException {
    String name = null;
    if (validation instanceof FXRequired) {
      FXRequired fXRequired = (FXRequired) validation;
      try {
        Method method = fXRequired.getClass().getMethod("validation");
        Class result = (Class) method.invoke(fXRequired, (Object[]) null);
        name = result.getName();
      } catch (NoSuchMethodException | SecurityException | InvocationTargetException ex) {
        Logger.getLogger(DefaultFXValidationHandler1.class.getName()).log(Level.SEVERE, null, ex);
      }

    } else if (validation instanceof FXString) {
      FXString fXString = (FXString) validation;
      try {
        Method method = fXString.getClass().getMethod("validation");
        Class result = (Class) method.invoke(fXString, (Object[]) null);
        name = result.getName();
      } catch (NoSuchMethodException | SecurityException | InvocationTargetException ex) {
        Logger.getLogger(DefaultFXValidationHandler1.class.getName()).log(Level.SEVERE, null, ex);
      }

    } else if (validation instanceof FXNotNull) {
      FXNotNull fXNotNull = (FXNotNull) validation;
      try {
        Method method = fXNotNull.getClass().getMethod("validation");
        Class result = (Class) method.invoke(fXNotNull, (Object[]) null);
        name = result.getName();
      } catch (NoSuchMethodException | SecurityException | InvocationTargetException ex) {
        Logger.getLogger(DefaultFXValidationHandler1.class.getName()).log(Level.SEVERE, null, ex);
      }

    } else if (validation instanceof FXNumber) {
      FXNumber fXNumber = (FXNumber) validation;
      try {
        Method method = fXNumber.getClass().getMethod("validation");
        Class result = (Class) method.invoke(fXNumber, (Object[]) null);
        name = result.getName();
      } catch (NoSuchMethodException | SecurityException | InvocationTargetException ex) {
        Logger.getLogger(DefaultFXValidationHandler1.class.getName()).log(Level.SEVERE, null, ex);
      }

    }
    return name;
  }
}
