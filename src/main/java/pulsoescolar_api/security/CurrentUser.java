package pulsoescolar_api.security;

import pulsoescolar_api.entity.SchoolUser;

public interface CurrentUser {
    SchoolUser get();
}
