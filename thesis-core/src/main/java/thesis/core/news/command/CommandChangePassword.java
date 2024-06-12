package thesis.core.news.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class CommandChangePassword {
    private String email;
    private String oldPassword;
    private String password;
    private String otp;
}
