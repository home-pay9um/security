package kr.kro.pay9um.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import kr.kro.pay9um.dto.validation.PasswordMatch;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@PasswordMatch
public class SignupRequest {
    @NotBlank(message = "아이디는 필수 입력 값입니다.")
    @Size(min = 4, max = 16, message = "아이디는 4~16자 사이여야 합니다.")
    @Pattern(
            regexp = "^[a-z0-9]+$",
            message = "아이디는 영문 소문자와 숫자만 사용 가능합니다."
    )
    private String username; // 로그인용 아이디

    @NotBlank(message = "비밀번호는 필수 입력 값입니다.")
    @Size(min = 8, max = 20, message = "비밀번호는 8~20자 사이여야 합니다.")
    @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[$@$!%*#?&])[A-Za-z\\d$@$!%*#?&]{8,20}$",
            message = "비밀번호는 8~20자 영문, 숫자, 특수문자를 최소 1개씩 포함해야 합니다."
    )
    private String password;

    @NotBlank(message = "비밀번호 확인은 필수 입력 값입니다.")
    private String passwordConfirm;

    @NotBlank(message = "이메일은 필수 입력 값입니다.")
    @Size(max = 100, message = "이메일은 최대 100자까지 입력 가능합니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    @Pattern(
            regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$",
            message = "올바른 이메일 형식이 아닙니다."
    )
    private String email;

    @NotBlank(message = "닉네임은 필수 입력 값입니다.")
    @Size(min = 2, max = 16, message = "닉네임은 2~16자 사이여야 합니다.")
    @Pattern(
            regexp = "^[가-힣a-zA-Z0-9]+(\\s[가-힣a-zA-Z0-9]+)*$",
            message = "닉네임은 특수문자 없이 한글, 영문, 숫자, 단어 사이 공백만 사용 가능합니다."
    )
    private String nickname;

    @NotBlank(message = "전화번호는 필수 입력 값입니다.")
    @Pattern(
            regexp = "^01(?:0|1|[6-9])-(?:\\d{3}|\\d{4})-\\d{4}$",
            message = "올바른 전화번호 형식이 아닙니다. (예: 010-1234-5678)"
    )
    private String phoneNumber;
}
