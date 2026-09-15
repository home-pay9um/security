package kr.kro.pay9um.dto.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import kr.kro.pay9um.dto.request.SignupRequest;

public class PasswordMatchValidator implements ConstraintValidator<PasswordMatch, SignupRequest> {
    @Override
    public boolean isValid(SignupRequest value, ConstraintValidatorContext context) {
        if (value.getPassword() == null || value.getPasswordConfirm() == null) {
            return true;
        }
        boolean isValid = value.getPassword().equals(value.getPasswordConfirm());
        if (!isValid) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("passwordConfirm")
                    .addConstraintViolation();
        }
        return isValid;
    }
}
