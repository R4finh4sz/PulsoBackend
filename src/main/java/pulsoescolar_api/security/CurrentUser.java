package pulsoescolar_api.security;

import pulsoescolar_api.entity.user.SchoolUser;

public interface CurrentUser {
    SchoolUser get();
}
