export type AppUserType = "GUEST" | "REGISTERED";

export type AuthSession = {
  userId: string;
  userType: AppUserType;
  displayName: string;
};
