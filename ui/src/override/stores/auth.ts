import {defineStore} from "pinia";
import {apiUrl} from "override/utils/route";
import {useAxios} from "../../utils/axios";

export class Me {
    username?: string;
    roles: string[] = [];

    constructor(username?: string, roles: string[] = []) {
        this.username = username;
        this.roles = roles;
    }

    /**
     * Check if user has a specific role
     */
    hasRole(role: string): boolean {
        return this.roles.includes(role) || this.roles.includes('ROLE_ADMIN');
    }

    /**
     * Check if user has any of the provided roles
     */
    hasAnyRoles(...roles: string[]): boolean {
        return roles.some(role => this.hasRole(role));
    }

    hasAny(_permission: any, _namespace?: any) {
        return true;
    }

    hasAnyAction(_permission: any, _action: any, _namespace?: any) {
        return true;
    }

    isAllowed(_permission: any, _action: any, _namespace: any) {
        // For OSS with OAuth, check if user has ROLE_ADMIN for write operations
        const writeActions = ['CREATE', 'UPDATE', 'DELETE'];
        if (writeActions.includes(_action)) {
            return this.hasRole('ROLE_ADMIN');
        }
        // Allow read operations for authenticated users
        return true;
    }

    isAllowedGlobal(_permission: any, _action: any) {
        const writeActions = ['CREATE', 'UPDATE', 'DELETE'];
        if (writeActions.includes(_action)) {
            return this.hasRole('ROLE_ADMIN');
        }
        return true;
    }

    hasAnyActionOnAnyNamespace(_permission: any, _action: any) {
        const writeActions = ['CREATE', 'UPDATE', 'DELETE'];
        if (writeActions.includes(_action)) {
            return this.hasRole('ROLE_ADMIN');
        }
        return true;
    }

    getNamespacesForAction(_permission: any, _action: any): string[] {
        return [];
    }
}

export const useAuthStore = defineStore("auth", {
    state: () => ({
        user: new Me(),
        isLogged: true,
    }),
    actions: {
        async loadUser() {
            try {
                const axios = useAxios();
                const response = await axios.get(`${apiUrl()}/auth/me`);
                this.user = new Me(response.data.username, response.data.roles);
                this.isLogged = true;
                return this.user;
            } catch (error) {
                console.error('Failed to load user profile:', error);
                this.user = new Me();
                this.isLogged = false;
                throw error;
            }
        },
        logout(){
            this.user = new Me();
            this.isLogged = false;
            return Promise.resolve(true);
        },
        correction(){
            return Promise.resolve(true);
        }
    },
})
