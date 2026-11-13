package fr.insastronaute.webapi.common.web;

import fr.insastronaute.webapi.friend.controller.FriendController;
import fr.insastronaute.webapi.notification.controller.NotificationController;
import fr.insastronaute.webapi.user.controller.AdminUserController;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;

class PaginationControllerResponseTest {

    @Test
    void paginationEndpointsReturnTheExplicitPageResponseContract() {
        assertThat(method(AdminUserController.class, "searchUsers").getReturnType()).isEqualTo(PageResponse.class);
        assertThat(method(FriendController.class, "searchFriends").getReturnType()).isEqualTo(PageResponse.class);
        assertThat(method(FriendController.class, "searchDepartmentPeopleAndFriends").getReturnType()).isEqualTo(PageResponse.class);
        assertThat(method(FriendController.class, "getIncomingRequests").getReturnType()).isEqualTo(PageResponse.class);
        assertThat(method(FriendController.class, "getOutgoingRequests").getReturnType()).isEqualTo(PageResponse.class);
        assertThat(method(NotificationController.class, "getMyNotifications").getReturnType()).isEqualTo(PageResponse.class);
    }

    private Method method(Class<?> controllerType, String name) {
        return java.util.Arrays.stream(controllerType.getDeclaredMethods())
                .filter(method -> method.getName().equals(name))
                .filter(method -> java.util.Arrays.asList(method.getParameterTypes()).contains(Pageable.class))
                .findFirst()
                .orElseThrow();
    }
}
