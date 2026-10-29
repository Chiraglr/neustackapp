package org.example.neustackapp.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RequestDto {

    @NotBlank(message = "Name cannot be blank or null")
    @Size(min = 2, max = 50, message = "Name must be between 2 and 50 characters")
    private String name;

    @NotBlank(message = "Email cannot be blank or null")
    @Email(message = "Email format must be valid")
    private String email;
}