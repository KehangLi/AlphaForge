export type AppUserType = "GUEST" | "REGISTERED";

export type AuthSession = {
  userId: string;
  userType: AppUserType;
  displayName: string;
};

export type LoginRequest = {
  email: string;
  password: string;
};

export type RegisterRequest = LoginRequest & {
  displayName: string;
};
