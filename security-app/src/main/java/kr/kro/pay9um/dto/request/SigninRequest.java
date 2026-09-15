package kr.kro.pay9um.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SigninRequest {
    @NotBlank(message = "아이디를 입력해주세요.")
    @Size(max = 16, message = "아이디 입력 길이가 올바르지 않습니다.")
    private String username;

    @NotBlank(message = "비밀번호를 입력해주세요.")
    @Size(max = 20, message = "비밀번호 입력 길이가 올바르지 않습니다.")
    private String password;
}
