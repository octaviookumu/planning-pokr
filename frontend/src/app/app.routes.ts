import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./features/home/home.component').then((m) => m.HomeComponent),
  },
  {
    path: 'rooms/:roomId',
    loadComponent: () => import('./features/room/room.component').then((m) => m.RoomComponent),
  },
  { path: '**', redirectTo: '' },
];
