import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./features/home/home').then((m) => m.HomeComponent),
  },
  {
    path: 'rooms/:roomId',
    loadComponent: () =>
      import('./features/room/room').then((m) => m.RoomComponent),
  },
  { path: '**', redirectTo: '' },
];
