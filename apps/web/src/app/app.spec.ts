import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { App } from './app';

describe('App', () => {
  it('renders the sidebar and navbar around the outlet', async () => {
    TestBed.configureTestingModule({ imports: [App], providers: [provideRouter([]), provideHttpClient()] });
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('app-sidebar .nav-item')?.getAttribute('href')).toBe('/agents');
    expect(el.querySelector('app-navbar nav')).toBeTruthy();
    expect(el.querySelector('main router-outlet')).toBeTruthy();
  });
});
