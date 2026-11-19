import { lazy } from 'react';
import { Route, Routes } from 'react-router';
import { GuestOnly, RequireRole, RequireVerifiedDonor } from './auth/RouteGuards';
import { DemoPage } from './features/auth/DemoPage';
import { ForgotPasswordPage } from './features/auth/ForgotPasswordPage';
import { LoginPage } from './features/auth/LoginPage';
import { ResetPasswordPage } from './features/auth/ResetPasswordPage';
import { SignUpPage } from './features/auth/SignUpPage';
import { AppLayout } from './layout/AppLayout';
import { NotFoundPage } from './pages/ErrorPages';

// The signed-in pages load on first use, so each role downloads only its own screens (Log in stays in the
// first download). AppLayout shows the loading state while a page's code arrives.
const page = (load, name) => lazy(() => load().then((module) => ({ default: module[name] })));
const AccountsPage = page(() => import('./features/admin/AccountsPage'), 'AccountsPage');
const ReportsPage = page(() => import('./features/admin/ReportsPage'), 'ReportsPage');
const ReportViewPage = page(() => import('./features/admin/ReportViewPage'), 'ReportViewPage');
const VerifyDonorsPage = page(() => import('./features/admin/VerifyDonorsPage'), 'VerifyDonorsPage');
const ClaimPage = page(() => import('./features/claims/ClaimPage'), 'ClaimPage');
const MyClaimsPage = page(() => import('./features/claims/MyClaimsPage'), 'MyClaimsPage');
const BoardPage = page(() => import('./features/donor/BoardPage'), 'BoardPage');
const DonorListingPage = page(() => import('./features/donor/DonorListingPage'), 'DonorListingPage');
const HistoryPage = page(() => import('./features/donor/HistoryPage'), 'HistoryPage');
const ListingFormPage = page(() => import('./features/donor/ListingFormPage'), 'ListingFormPage');
const FindFoodPage = page(() => import('./features/find/FindFoodPage'), 'FindFoodPage');
const ListingPage = page(() => import('./features/listing/ListingPage'), 'ListingPage');
const ProfilePage = page(() => import('./features/profile/ProfilePage'), 'ProfilePage');

const EVERY_ROLE = ['DONOR', 'RECEIVER', 'ADMIN'];

/** Every route of UI-UX.md "Routes and screens". The server checks every permission again. */
export function App() {
  return (
    <Routes>
      <Route element={<GuestOnly />}>
        <Route path="/" element={<LoginPage />} />
        <Route path="/signup" element={<SignUpPage />} />
        <Route path="/demo" element={<DemoPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      </Route>
      {/* Opened from the email: works whether or not someone is logged in on this browser. */}
      <Route path="/reset-password" element={<ResetPasswordPage />} />

      <Route element={<RequireRole roles={EVERY_ROLE} />}>
        <Route element={<AppLayout />}>
          <Route element={<RequireRole roles={['RECEIVER']} />}>
            <Route path="/home" element={<FindFoodPage />} />
            <Route path="/listings/:id" element={<ListingPage />} />
            <Route path="/claims" element={<MyClaimsPage />} />
          </Route>
          <Route element={<RequireRole roles={['RECEIVER', 'DONOR']} />}>
            <Route path="/claims/:id" element={<ClaimPage />} />
          </Route>
          <Route element={<RequireRole roles={['DONOR']} />}>
            <Route path="/donor" element={<BoardPage />} />
            <Route path="/donor/history" element={<HistoryPage />} />
            <Route element={<RequireVerifiedDonor />}>
              <Route path="/donor/listings/new" element={<ListingFormPage />} />
            </Route>
            <Route path="/donor/listings/:id" element={<DonorListingPage />} />
            <Route path="/donor/listings/:id/edit" element={<ListingFormPage />} />
          </Route>
          <Route element={<RequireRole roles={['ADMIN']} />}>
            <Route path="/admin/donors" element={<VerifyDonorsPage />} />
            <Route path="/admin/reports" element={<ReportsPage />} />
            <Route path="/admin/reports/:id" element={<ReportViewPage />} />
            <Route path="/admin/accounts" element={<AccountsPage />} />
          </Route>
          <Route path="/profile" element={<ProfilePage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Route>
    </Routes>
  );
}
