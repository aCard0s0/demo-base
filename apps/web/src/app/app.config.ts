import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { routes } from './app.routes';
import { Auth, authInterceptor } from './auth/auth';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withFetch(), withInterceptors([authInterceptor])),
    // Loads the stored token's user for the navbar. Not awaited: the page must not wait on auth-service, and a
    // dead token is dropped by the interceptor's 401.
    provideAppInitializer(() => {
      const auth = inject(Auth);
      if (auth.token()) auth.refresh().catch(() => {});
    }),
  ],
};
