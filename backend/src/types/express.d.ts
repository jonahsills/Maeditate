export {};

declare global {
  namespace Express {
    interface UserPayload {
      userId: string;
      deviceId: string;
    }

    // Augment Request with an optional user payload attached by auth middleware
    interface Request {
      user?: UserPayload;
    }
  }
}


