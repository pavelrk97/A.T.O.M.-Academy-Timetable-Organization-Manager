package ru.myapp.service;

import ru.myapp.dto.UserDto;

import java.util.List;

public interface UserService {
    List<UserDto> getAllUsers();
    UserDto getUserById(Long id);
    UserDto createUser(UserDto userDto);
    void deleteUser(Long id);
}